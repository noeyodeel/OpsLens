import json
import os
import time

import pika
import requests
from dotenv import load_dotenv

load_dotenv()

RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://opslens:opslens@localhost:5672/%2F")
QUEUE_NAME = os.getenv("ANALYSIS_JOB_QUEUE", "opslens.analysis.jobs")
OPSLENS_API_BASE_URL = os.getenv("OPSLENS_API_BASE_URL", "http://localhost:8080").rstrip("/")
OPSLENS_DASHBOARD_BASE_URL = os.getenv(
    "OPSLENS_DASHBOARD_BASE_URL",
    "http://localhost:5173",
).rstrip("/")
REQUEST_TIMEOUT_SECONDS = int(os.getenv("REQUEST_TIMEOUT_SECONDS", "30"))


def patch_job_status(job_id, status, error_message=None):
    payload = {"status": status}
    if error_message:
        payload["errorMessage"] = error_message[:2000]
    response = requests.patch(
        f"{OPSLENS_API_BASE_URL}/api/analysis-jobs/{job_id}/status",
        json=payload,
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    response.raise_for_status()


def run_analysis(incident_no):
    response = requests.post(
        f"{OPSLENS_API_BASE_URL}/api/incidents/{incident_no}/analyze",
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    return response.json()


def notify_slack(response_url, payload):
    if not response_url:
        return
    response = requests.post(
        response_url,
        json=payload,
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    response.raise_for_status()


def try_notify_slack(response_url, payload):
    try:
        notify_slack(response_url, payload)
    except Exception as exc:
        print(f"failed to send Slack response: {exc}", flush=True)


def build_success_slack_payload(job_id, incident_no, analysis):
    summary = analysis.get("summary") or "Analysis completed."
    impact_scope = analysis.get("impactScope") or "-"
    suspected_causes = analysis.get("suspectedCauses") or []
    top_cause = suspected_causes[0] if suspected_causes else None
    cause_text = "-"
    if top_cause:
        cause_text = top_cause.get("cause") or "-"
        confidence = top_cause.get("confidence")
        if confidence is not None:
            cause_text = f"{cause_text} (confidence: {confidence})"

    dashboard_url = f"{OPSLENS_DASHBOARD_BASE_URL}/incidents/{incident_no}"
    text = (
        f"분석이 완료되었습니다.\n"
        f"Job ID: {job_id}\n"
        f"Incident: {incident_no}\n"
        f"요약: {summary}\n"
        f"영향 범위: {impact_scope}\n"
        f"가장 가능성 높은 원인: {cause_text}\n"
        f"상세 보기: {dashboard_url}"
    )
    return {
        "response_type": "in_channel",
        "replace_original": False,
        "text": text,
    }


def build_failure_slack_payload(job_id, incident_no, error_message):
    return {
        "response_type": "ephemeral",
        "replace_original": False,
        "text": (
            f"분석 작업이 실패했습니다.\n"
            f"Job ID: {job_id}\n"
            f"Incident: {incident_no}\n"
            f"오류: {error_message}"
        ),
    }


def handle_message(channel, method, properties, body):
    message = json.loads(body.decode("utf-8"))
    job_id = message["jobId"]
    incident_no = message["incidentNo"]
    response_url = message.get("slackResponseUrl")
    print(f"received analysis job {job_id} for {incident_no}", flush=True)

    try:
        patch_job_status(job_id, "RUNNING")
        analysis = run_analysis(incident_no)
        patch_job_status(job_id, "SUCCEEDED")
        try_notify_slack(
            response_url,
            build_success_slack_payload(job_id, incident_no, analysis),
        )
        print(
            f"completed analysis job {job_id}; analysis id={analysis.get('id')}",
            flush=True,
        )
        channel.basic_ack(delivery_tag=method.delivery_tag)
    except Exception as exc:
        error_message = str(exc)
        print(f"failed analysis job {job_id}: {error_message}", flush=True)
        try:
            patch_job_status(job_id, "FAILED", error_message)
            try_notify_slack(
                response_url,
                build_failure_slack_payload(job_id, incident_no, error_message),
            )
        finally:
            channel.basic_ack(delivery_tag=method.delivery_tag)


def connect_with_retry():
    while True:
        try:
            return pika.BlockingConnection(pika.URLParameters(RABBITMQ_URL))
        except pika.exceptions.AMQPConnectionError:
            print("waiting for RabbitMQ...", flush=True)
            time.sleep(3)


def main():
    connection = connect_with_retry()
    channel = connection.channel()
    channel.queue_declare(queue=QUEUE_NAME, durable=True)
    channel.basic_qos(prefetch_count=1)
    channel.basic_consume(queue=QUEUE_NAME, on_message_callback=handle_message)
    print(f"AI worker consuming {QUEUE_NAME}", flush=True)
    channel.start_consuming()


if __name__ == "__main__":
    main()
