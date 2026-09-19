package com.opslens.application.analysis;

import java.time.Instant;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.opslens.application.incident.IncidentNotFoundException;
import com.opslens.domain.analysis.AnalysisJob;
import com.opslens.domain.analysis.AnalysisJobRepository;
import com.opslens.domain.analysis.AnalysisJobStatus;
import com.opslens.domain.incident.Incident;
import com.opslens.domain.incident.IncidentRepository;

@Service
public class AnalysisJobService {

    private final IncidentRepository incidentRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final RabbitTemplate rabbitTemplate;

    public AnalysisJobService(
        IncidentRepository incidentRepository,
        AnalysisJobRepository analysisJobRepository,
        RabbitTemplate rabbitTemplate
    ) {
        this.incidentRepository = incidentRepository;
        this.analysisJobRepository = analysisJobRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public AnalysisJobView requestAnalysis(AnalysisJobRequest request) {
        Incident incident = incidentRepository.findByIncidentNo(request.incidentNo())
            .orElseThrow(() -> new IncidentNotFoundException(request.incidentNo()));
        Instant createdAt = Instant.now();
        AnalysisJob job = analysisJobRepository.save(new AnalysisJob(
            newJobId(),
            incident,
            request.requestedBy(),
            request.requestText(),
            request.slackChannelId(),
            request.slackThreadTs(),
            request.slackResponseUrl(),
            createdAt
        ));
        AnalysisJobMessage message = new AnalysisJobMessage(
            job.getJobId(),
            incident.getIncidentNo(),
            job.getRequestedBy(),
            job.getRequestText(),
            job.getSlackChannelId(),
            job.getSlackThreadTs(),
            job.getSlackResponseUrl(),
            job.getCreatedAt()
        );
        publishAfterCommit(message);
        return AnalysisJobView.from(job);
    }

    @Transactional(readOnly = true)
    public AnalysisJobView getJob(String jobId) {
        return AnalysisJobView.from(findJob(jobId));
    }

    @Transactional
    public AnalysisJobView updateStatus(String jobId, AnalysisJobStatusUpdate update) {
        AnalysisJob job = findJob(jobId);
        Instant now = Instant.now();
        switch (update.status()) {
            case RUNNING -> job.markRunning(now);
            case SUCCEEDED -> job.markSucceeded(now);
            case FAILED -> job.markFailed(update.errorMessage(), now);
            case PENDING -> throw new IllegalArgumentException("Cannot move an existing job back to PENDING");
        }
        return AnalysisJobView.from(job);
    }

    private AnalysisJob findJob(String jobId) {
        return analysisJobRepository.findByJobId(jobId)
            .orElseThrow(() -> new AnalysisJobNotFoundException(jobId));
    }

    private String newJobId() {
        return "JOB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private void publishAfterCommit(AnalysisJobMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(message);
            }
        });
    }

    private void publish(AnalysisJobMessage message) {
        rabbitTemplate.convertAndSend(
            AnalysisJobQueueConfig.EXCHANGE_NAME,
            AnalysisJobQueueConfig.ROUTING_KEY,
            message
        );
    }

    public record AnalysisJobRequest(
        String incidentNo,
        String requestedBy,
        String requestText,
        String slackChannelId,
        String slackThreadTs,
        String slackResponseUrl
    ) {
    }

    public record AnalysisJobStatusUpdate(
        AnalysisJobStatus status,
        String errorMessage
    ) {
    }

    public record AnalysisJobView(
        String jobId,
        String incidentNo,
        AnalysisJobStatus status,
        String requestedBy,
        String requestText,
        String slackChannelId,
        String slackThreadTs,
        String slackResponseUrl,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
    ) {

        private static AnalysisJobView from(AnalysisJob job) {
            return new AnalysisJobView(
                job.getJobId(),
                job.getIncident().getIncidentNo(),
                job.getStatus(),
                job.getRequestedBy(),
                job.getRequestText(),
                job.getSlackChannelId(),
                job.getSlackThreadTs(),
                job.getSlackResponseUrl(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt()
            );
        }
    }
}
