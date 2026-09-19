package com.opslens.application.analysis;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.opslens.application.analysis.AiIncidentContextBuilderService.AiIncidentContext;
import com.opslens.domain.incident.AnomalyType;

@Component
public class MockIncidentAnalysisClient implements IncidentAnalysisClient {

    @Override
    public IncidentAnalysisResult analyze(AiIncidentContext context) {
        AnomalyType anomalyType = context.incident().anomalyType();
        return switch (anomalyType) {
            case SOURCE_MISSING -> sourceMissingAnalysis(context);
            case COUNT_DROP -> countDropAnalysis(context);
            case NULL_SPIKE -> nullSpikeAnalysis(context);
            case DUPLICATE_DETECTED -> duplicateAnalysis(context);
            case PROCESSING_FAILURE_SPIKE -> processingFailureAnalysis(context);
            case COUNT_SPIKE -> countSpikeAnalysis(context);
        };
    }

    private IncidentAnalysisResult sourceMissingAnalysis(AiIncidentContext context) {
        String institutionCode = institutionCode(context);
        return new IncidentAnalysisResult(
            "%s 기관의 %s 진료 전송 데이터가 수신되지 않았습니다.".formatted(institutionCode, context.incident().analysisDate()),
            "해당 일자의 %s 기관 진료 데이터가 서비스 DB에 누락되었을 가능성이 있습니다.".formatted(institutionCode),
            List.of(new SuspectedCause(
                1,
                "외부 기관 전송 실패",
                "현재 수신 건수가 0건이며, 수신 로그에서 실패 또는 미수신 여부를 확인할 수 있습니다.",
                new BigDecimal("0.8500")
            )),
            List.of(
                ingestionLogSql(context),
                treatmentRecordCountSql(context)
            ),
            List.of(
                "대상 배치 일자에 기관이 파일 또는 API 데이터를 전송했는지 확인합니다.",
                "treatment_records 저장 전에 수신 작업이 실패했는지 확인합니다."
            ),
            true
        );
    }

    private IncidentAnalysisResult countDropAnalysis(AiIncidentContext context) {
        String institutionCode = institutionCode(context);
        return new IncidentAnalysisResult(
            "%s 기관의 진료 전송 데이터 건수가 설정된 정상 기준보다 낮습니다.".formatted(institutionCode),
            "%s 기관의 일부 데이터가 지연, 제외 또는 아직 적재되지 않았을 수 있습니다.".formatted(institutionCode),
            List.of(new SuspectedCause(
                1,
                "기관 데이터 일부 전송 누락",
                "현재 건수가 정상 기준보다 낮지만 일부 데이터는 수신되어 전체 미수신보다는 부분 누락 가능성이 높습니다.",
                new BigDecimal("0.7800")
            )),
            List.of(
                treatmentRecordCountSql(context),
                ingestionLogSql(context)
            ),
            List.of(
                "received_count와 success_count를 정상 기준 기간과 비교합니다.",
                "특정 상태의 진료 전송 데이터만 누락되었는지 확인합니다."
            ),
            true
        );
    }

    private IncidentAnalysisResult nullSpikeAnalysis(AiIncidentContext context) {
        String institutionCode = institutionCode(context);
        return new IncidentAnalysisResult(
            "%s 기관의 필수 항목 NULL 비율이 증가했습니다.".formatted(institutionCode),
            "데이터는 수신되었지만 일부 필수 값이 비어 있어 이후 진료내역 확인 화면에 영향을 줄 수 있습니다.",
            List.of(new SuspectedCause(
                1,
                "상위 시스템 매핑 변경 또는 필수 값 누락",
                "전체 전송 장애보다는 특정 필드 품질 문제가 발생한 것으로 보입니다.",
                new BigDecimal("0.7600")
            )),
            List.of(ingestionLogSql(context)),
            List.of(
                "NULL 증가가 가장 큰 필수 항목을 확인합니다.",
                "대상 일자 전후의 기관 전송 데이터 매핑을 비교합니다."
            ),
            true
        );
    }

    private IncidentAnalysisResult duplicateAnalysis(AiIncidentContext context) {
        String institutionCode = institutionCode(context);
        return new IncidentAnalysisResult(
            "%s 기관의 검증 레코드 키 중복이 탐지되었습니다.".formatted(institutionCode),
            "중복 키가 정리되기 전까지 검증 결과가 중복 집계될 수 있습니다.",
            List.of(new SuspectedCause(
                1,
                "기관 재전송 반복 또는 멱등성 처리 누락",
                "동일한 기관 전송 데이터가 중복 제거 장치 없이 재처리될 때 중복 키가 발생할 수 있습니다.",
                new BigDecimal("0.7400")
            )),
            List.of(verificationDuplicateSql(context)),
            List.of(
                "동일 배치가 여러 번 수신되었는지 확인합니다.",
                "재처리 시 외부 레코드 키가 동일하게 유지되는지 확인합니다."
            ),
            true
        );
    }

    private IncidentAnalysisResult processingFailureAnalysis(AiIncidentContext context) {
        return new IncidentAnalysisResult(
            "영향 배치에서 처리 실패 건수가 증가했습니다.",
            "수신된 데이터 중 일부가 정상적으로 저장되지 않았을 수 있습니다.",
            List.of(new SuspectedCause(
                1,
                "배치 처리 실패",
                "수신 로그의 failed_count와 FAILED 상태를 확인해야 합니다.",
                new BigDecimal("0.7700")
            )),
            List.of(ingestionLogSql(context)),
            List.of("수신 작업의 started_at, ended_at 시각 주변 백엔드 로그를 확인합니다."),
            true
        );
    }

    private IncidentAnalysisResult countSpikeAnalysis(AiIncidentContext context) {
        return new IncidentAnalysisResult(
            "진료 전송 데이터 건수가 설정된 정상 기준보다 증가했습니다.",
            "대상 일자에 예상보다 많은 데이터가 서비스에 적재되었을 수 있습니다.",
            List.of(new SuspectedCause(
                1,
                "중복 수신 또는 기관 측 전송량 증가",
                "급격한 건수 증가는 중복 적재 또는 기관 측의 실제 전송량 증가로 발생할 수 있습니다.",
                new BigDecimal("0.6800")
            )),
            List.of(
                treatmentRecordCountSql(context),
                ingestionLogSql(context)
            ),
            List.of("기관별 건수를 최근 정상 기준 일자와 비교합니다."),
            true
        );
    }

    private VerificationSql ingestionLogSql(AiIncidentContext context) {
        String institutionFilter = institutionCode(context).equals("-")
            ? ""
            : " and ei.code = '%s'".formatted(institutionCode(context));
        return new VerificationSql(
            "영향 배치 수신 로그 확인",
            "수신 건수, 성공 건수, 실패 건수와 배치 상태를 확인합니다.",
            """
            select ei.code as institution_code,
                   dil.target_table,
                   dil.batch_date,
                   dil.received_count,
                   dil.success_count,
                   dil.failed_count,
                   dil.status
            from data_ingestion_log dil
            join external_institution ei on ei.id = dil.external_institution_id
            where dil.target_table = '%s'
              and dil.batch_date = date '%s'%s
            order by ei.code
            """.formatted(context.incident().targetTable(), context.incident().analysisDate(), institutionFilter).strip()
        );
    }

    private VerificationSql treatmentRecordCountSql(AiIncidentContext context) {
        String institutionFilter = institutionCode(context).equals("-")
            ? ""
            : " and ei.code = '%s'".formatted(institutionCode(context));
        return new VerificationSql(
            "기관별 진료 전송 데이터 건수 확인",
            "이상이 특정 기관에만 발생했는지 확인합니다.",
            """
            select ei.code as institution_code,
                   count(*) as record_count
            from treatment_records tr
            join external_institution ei on ei.id = tr.external_institution_id
            where tr.recorded_at >= timestamp '%s 00:00:00'
              and tr.recorded_at < timestamp '%s 00:00:00' + interval '1 day'%s
            group by ei.code
            order by ei.code
            """.formatted(context.incident().analysisDate(), context.incident().analysisDate(), institutionFilter).strip()
        );
    }

    private VerificationSql verificationDuplicateSql(AiIncidentContext context) {
        String institutionFilter = institutionCode(context).equals("-")
            ? ""
            : " and ei.code = '%s'".formatted(institutionCode(context));
        return new VerificationSql(
            "중복 검증 레코드 키 확인",
            "두 번 이상 나타난 검증 레코드 키를 조회합니다.",
            """
            select ei.code as institution_code,
                   vr.external_record_key,
                   count(*) as duplicate_count
            from verification_records vr
            join external_institution ei on ei.id = vr.external_institution_id
            where vr.created_at >= timestamp '%s 00:00:00'
              and vr.created_at < timestamp '%s 00:00:00' + interval '1 day'%s
            group by ei.code, vr.external_record_key
            having count(*) > 1
            order by duplicate_count desc
            """.formatted(context.incident().analysisDate(), context.incident().analysisDate(), institutionFilter).strip()
        );
    }

    private String institutionCode(AiIncidentContext context) {
        String institutionCode = context.incident().targetInstitutionCode();
        return institutionCode == null || institutionCode.isBlank() ? "-" : institutionCode;
    }
}
