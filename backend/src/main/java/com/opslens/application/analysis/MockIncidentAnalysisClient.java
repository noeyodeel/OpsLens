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
            "No treatment records were received from %s on %s.".formatted(institutionCode, context.incident().analysisDate()),
            "Treatment records from %s may be missing from the service database for the affected date.".formatted(institutionCode),
            List.of(new SuspectedCause(
                1,
                "External institution transmission failure",
                "The incident metric shows zero current records and the related ingestion log can be checked for failed or missing intake.",
                new BigDecimal("0.8500")
            )),
            List.of(
                ingestionLogSql(context),
                treatmentRecordCountSql(context)
            ),
            List.of(
                "Confirm whether the institution sent a file or API payload for the target batch date.",
                "Check whether the ingestion job failed before records were written to treatment_records."
            ),
            true
        );
    }

    private IncidentAnalysisResult countDropAnalysis(AiIncidentContext context) {
        String institutionCode = institutionCode(context);
        return new IncidentAnalysisResult(
            "Treatment record volume for %s dropped below the configured baseline.".formatted(institutionCode),
            "Some records from %s may be delayed, filtered out, or not yet ingested.".formatted(institutionCode),
            List.of(new SuspectedCause(
                1,
                "Partial institution data transmission",
                "The current record count is lower than the baseline while the institution still has non-zero records.",
                new BigDecimal("0.7800")
            )),
            List.of(
                treatmentRecordCountSql(context),
                ingestionLogSql(context)
            ),
            List.of(
                "Compare received_count and success_count with the baseline period.",
                "Check whether only specific record statuses are missing."
            ),
            true
        );
    }

    private IncidentAnalysisResult nullSpikeAnalysis(AiIncidentContext context) {
        String institutionCode = institutionCode(context);
        return new IncidentAnalysisResult(
            "Required field NULL ratio increased for %s.".formatted(institutionCode),
            "Records may be present but incomplete, which can affect downstream verification screens.",
            List.of(new SuspectedCause(
                1,
                "Changed upstream mapping or missing required field",
                "The anomaly points to a field quality issue rather than a full transmission outage.",
                new BigDecimal("0.7600")
            )),
            List.of(ingestionLogSql(context)),
            List.of(
                "Identify which required field has the highest NULL increase.",
                "Compare the institution payload mapping before and after the target date."
            ),
            true
        );
    }

    private IncidentAnalysisResult duplicateAnalysis(AiIncidentContext context) {
        String institutionCode = institutionCode(context);
        return new IncidentAnalysisResult(
            "Duplicate verification keys were detected for %s.".formatted(institutionCode),
            "Verification results may be counted more than once until duplicate keys are reviewed.",
            List.of(new SuspectedCause(
                1,
                "Repeated upstream resend or idempotency gap",
                "Duplicate keys usually appear when the same institution payload is reprocessed without a stable de-duplication guard.",
                new BigDecimal("0.7400")
            )),
            List.of(verificationDuplicateSql(context)),
            List.of(
                "Check whether the same batch was received multiple times.",
                "Confirm that retry processing preserves the same external record key."
            ),
            true
        );
    }

    private IncidentAnalysisResult processingFailureAnalysis(AiIncidentContext context) {
        return new IncidentAnalysisResult(
            "Processing failures increased for the affected intake batch.",
            "Some received records may not have been persisted successfully.",
            List.of(new SuspectedCause(
                1,
                "Batch processing failure",
                "The ingestion evidence should be reviewed for failed_count and FAILED status.",
                new BigDecimal("0.7700")
            )),
            List.of(ingestionLogSql(context)),
            List.of("Inspect backend logs around the ingestion started_at and ended_at timestamps."),
            true
        );
    }

    private IncidentAnalysisResult countSpikeAnalysis(AiIncidentContext context) {
        return new IncidentAnalysisResult(
            "Treatment record volume increased above the configured baseline.",
            "The service may contain unexpected extra records for the target date.",
            List.of(new SuspectedCause(
                1,
                "Duplicate or expanded upstream transmission",
                "A sudden count spike can be caused by duplicate ingestion or a legitimate institution-side volume increase.",
                new BigDecimal("0.6800")
            )),
            List.of(
                treatmentRecordCountSql(context),
                ingestionLogSql(context)
            ),
            List.of("Compare institution-level counts with recent baseline dates."),
            true
        );
    }

    private VerificationSql ingestionLogSql(AiIncidentContext context) {
        String institutionFilter = institutionCode(context).equals("-")
            ? ""
            : " and ei.code = '%s'".formatted(institutionCode(context));
        return new VerificationSql(
            "Check ingestion log for the affected batch",
            "Confirm received, success, failed counts and batch status.",
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
            "Count treatment records by institution",
            "Verify whether the anomaly is isolated to one institution.",
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
            "Find duplicate verification keys",
            "List verification record keys that appear more than once.",
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
