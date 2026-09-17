package com.opslens.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.opslens.domain.datasource.DataIngestionLog;
import com.opslens.domain.datasource.DataIngestionLogRepository;
import com.opslens.domain.datasource.ExternalInstitution;
import com.opslens.domain.datasource.ExternalInstitutionRepository;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.incident.DetectionRule;
import com.opslens.domain.incident.DetectionRuleRepository;
import com.opslens.domain.incident.Incident;
import com.opslens.domain.incident.IncidentMetricSnapshot;
import com.opslens.domain.incident.IncidentMetricSnapshotRepository;
import com.opslens.domain.incident.IncidentRepository;
import com.opslens.domain.incident.IncidentSeverity;
import com.opslens.domain.incident.MetricType;
import com.opslens.domain.incident.ThresholdType;

@DataJpaTest(showSql = false)
@Import(AiIncidentContextBuilderService.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AiIncidentContextBuilderServiceTest {

    @Autowired
    private ExternalInstitutionRepository externalInstitutionRepository;

    @Autowired
    private DataIngestionLogRepository dataIngestionLogRepository;

    @Autowired
    private DetectionRuleRepository detectionRuleRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentMetricSnapshotRepository metricSnapshotRepository;

    @Autowired
    private AiIncidentContextBuilderService aiIncidentContextBuilderService;

    @BeforeEach
    void setUp() {
        ExternalInstitution institution = externalInstitutionRepository.save(new ExternalInstitution(
            "INST_02",
            "Beta Medical Center",
            Instant.parse("2026-09-01T00:00:00Z")
        ));
        DetectionRule rule = detectionRuleRepository.save(new DetectionRule(
            "Institution daily data missing",
            "treatment_records",
            MetricType.SOURCE_COUNT,
            ThresholdType.EQUALS_ZERO,
            new BigDecimal("0.0000")
        ));
        Incident incident = incidentRepository.save(new Incident(
            "INC-20260913-SOURCE-MISSING-INST_02",
            rule,
            IncidentSeverity.CRITICAL,
            "treatment_records",
            "INST_02",
            AnomalyType.SOURCE_MISSING,
            "No treatment records were received from INST_02.",
            Instant.parse("2026-09-14T00:00:00Z")
        ));
        metricSnapshotRepository.save(new IncidentMetricSnapshot(
            incident,
            "treatment_records.daily.source_missing.INST_02",
            new BigDecimal("40.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("-100.0000"),
            Instant.parse("2026-09-14T00:00:00Z")
        ));
        dataIngestionLogRepository.save(new DataIngestionLog(
            institution,
            "treatment_records",
            java.time.LocalDate.of(2026, 9, 13),
            0,
            0,
            1,
            "FAILED",
            Instant.parse("2026-09-13T00:00:00Z"),
            Instant.parse("2026-09-13T00:05:00Z")
        ));
    }

    @Test
    void buildsBoundedContextForAiAnalysis() {
        var context = aiIncidentContextBuilderService.buildContext("INC-20260913-SOURCE-MISSING-INST_02");

        assertThat(context.incident().incidentNo()).isEqualTo("INC-20260913-SOURCE-MISSING-INST_02");
        assertThat(context.incident().analysisDate()).isEqualTo(java.time.LocalDate.of(2026, 9, 13));
        assertThat(context.incident().detectionRuleName()).isEqualTo("Institution daily data missing");
        assertThat(context.incident().metricType()).isEqualTo(MetricType.SOURCE_COUNT);
        assertThat(context.relatedMetrics()).singleElement()
            .satisfies(metric -> {
                assertThat(metric.metricName()).isEqualTo("treatment_records.daily.source_missing.INST_02");
                assertThat(metric.currentValue()).isEqualByComparingTo("0.0000");
                assertThat(metric.changeRate()).isEqualByComparingTo("-100.0000");
            });
        assertThat(context.ingestionLogs()).singleElement()
            .satisfies(log -> {
                assertThat(log.institutionCode()).isEqualTo("INST_02");
                assertThat(log.receivedCount()).isZero();
                assertThat(log.failedCount()).isEqualTo(1);
                assertThat(log.status()).isEqualTo("FAILED");
            });
        assertThat(context.schemaHints()).anyMatch(hint -> hint.contains("data_ingestion_log"));
        assertThat(context.safetyRules()).anyMatch(rule -> rule.contains("SELECT-only"));
    }
}
