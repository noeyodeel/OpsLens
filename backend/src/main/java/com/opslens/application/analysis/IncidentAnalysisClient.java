package com.opslens.application.analysis;

import java.math.BigDecimal;
import java.util.List;

import com.opslens.application.analysis.AiIncidentContextBuilderService.AiIncidentContext;

public interface IncidentAnalysisClient {

    IncidentAnalysisResult analyze(AiIncidentContext context);

    record IncidentAnalysisResult(
        String summary,
        String impactScope,
        List<SuspectedCause> suspectedCauses,
        List<VerificationSql> verificationSql,
        List<String> additionalChecks,
        boolean mock
    ) {
    }

    record SuspectedCause(
        int rank,
        String cause,
        String reason,
        BigDecimal confidence
    ) {
    }

    record VerificationSql(
        String title,
        String purpose,
        String sql,
        boolean safe,
        String safetyMessage
    ) {

        public VerificationSql(String title, String purpose, String sql) {
            this(title, purpose, sql, true, "SQL safety check has not run yet.");
        }
    }
}
