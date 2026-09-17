package com.opslens.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opslens.application.analysis.AiIncidentContextBuilderService.AiIncidentContext;
import com.opslens.application.analysis.AiIncidentContextBuilderService.IncidentFacts;
import com.opslens.application.analysis.AiIncidentContextBuilderService.IngestionEvidence;
import com.opslens.application.analysis.AiIncidentContextBuilderService.MetricEvidence;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.incident.IncidentSeverity;
import com.opslens.domain.incident.IncidentStatus;
import com.opslens.domain.incident.MetricType;
import com.opslens.domain.incident.ThresholdType;

class MockIncidentAnalysisClientTest {

    private final MockIncidentAnalysisClient client = new MockIncidentAnalysisClient();

    @Test
    void returnsStructuredAnalysisForSourceMissingIncident() {
        var result = client.analyze(context(AnomalyType.SOURCE_MISSING));

        assertThat(result.mock()).isTrue();
        assertThat(result.summary()).contains("No treatment records");
        assertThat(result.impactScope()).contains("INST_02");
        assertThat(result.suspectedCauses()).singleElement()
            .satisfies(cause -> {
                assertThat(cause.rank()).isEqualTo(1);
                assertThat(cause.cause()).contains("transmission");
                assertThat(cause.confidence()).isEqualByComparingTo("0.8500");
            });
        assertThat(result.verificationSql()).hasSize(2);
        assertThat(result.verificationSql().get(0).sql())
            .contains("select")
            .contains("data_ingestion_log")
            .contains("ei.code = 'INST_02'");
        assertThat(result.additionalChecks()).anyMatch(check -> check.contains("institution"));
    }

    @Test
    void returnsSelectOnlyVerificationSqlForDuplicateIncident() {
        var result = client.analyze(context(AnomalyType.DUPLICATE_DETECTED));

        assertThat(result.verificationSql()).singleElement()
            .satisfies(sql -> {
                assertThat(sql.sql()).contains("select");
                assertThat(sql.sql()).contains("verification_records");
                assertThat(sql.sql().toLowerCase()).doesNotContain("delete");
                assertThat(sql.sql().toLowerCase()).doesNotContain("update");
            });
    }

    private AiIncidentContext context(AnomalyType anomalyType) {
        return new AiIncidentContext(
            new IncidentFacts(
                1L,
                "INC-20260913-SOURCE-MISSING-INST_02",
                IncidentSeverity.CRITICAL,
                IncidentStatus.DETECTED,
                "treatment_records",
                "INST_02",
                anomalyType,
                "No treatment records were received from INST_02.",
                LocalDate.of(2026, 9, 13),
                Instant.parse("2026-09-14T00:00:00Z"),
                null,
                "Institution daily data missing",
                MetricType.SOURCE_COUNT,
                ThresholdType.EQUALS_ZERO,
                new BigDecimal("0.0000")
            ),
            List.of(new MetricEvidence(
                "treatment_records.daily.source_missing.INST_02",
                new BigDecimal("40.0000"),
                new BigDecimal("0.0000"),
                new BigDecimal("-100.0000"),
                Instant.parse("2026-09-14T00:00:00Z")
            )),
            List.of(new IngestionEvidence(
                "INST_02",
                "Beta Medical Center",
                "treatment_records",
                LocalDate.of(2026, 9, 13),
                0,
                0,
                1,
                "FAILED",
                Instant.parse("2026-09-13T00:00:00Z"),
                Instant.parse("2026-09-13T00:05:00Z")
            )),
            List.of("data_ingestion_log tracks batch status"),
            List.of("Generated SQL must be SELECT-only")
        );
    }
}
