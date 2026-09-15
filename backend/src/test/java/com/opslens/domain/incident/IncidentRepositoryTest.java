package com.opslens.domain.incident;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest(showSql = false)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class IncidentRepositoryTest {

    @Autowired
    private DetectionRuleRepository detectionRuleRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentMetricSnapshotRepository metricSnapshotRepository;

    @Test
    void savesIncidentWithRuleAndMetricSnapshot() {
        DetectionRule rule = detectionRuleRepository.save(new DetectionRule(
            "Treatment records daily volume drop",
            "treatment_records",
            MetricType.ROW_COUNT,
            ThresholdType.BELOW_RATIO,
            new BigDecimal("0.5000")
        ));
        Instant detectedAt = Instant.parse("2026-09-13T00:00:00Z");

        Incident incident = incidentRepository.save(new Incident(
            "INC-20260913-001",
            rule,
            IncidentSeverity.CRITICAL,
            "treatment_records",
            AnomalyType.COUNT_DROP,
            "Treatment record intake volume dropped 72%",
            detectedAt
        ));

        metricSnapshotRepository.save(new IncidentMetricSnapshot(
            incident,
            "treatment_records.daily.count",
            new BigDecimal("12000.0000"),
            new BigDecimal("3360.0000"),
            new BigDecimal("-72.0000"),
            detectedAt
        ));

        assertThat(detectionRuleRepository.findByEnabledTrue()).hasSize(1);
        assertThat(detectionRuleRepository.findByTargetTableAndEnabledTrue("treatment_records")).hasSize(1);
        assertThat(incidentRepository.findByIncidentNo("INC-20260913-001")).hasValueSatisfying(saved -> {
            assertThat(saved.getStatus()).isEqualTo(IncidentStatus.DETECTED);
            assertThat(saved.getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
            assertThat(saved.getAnomalyType()).isEqualTo(AnomalyType.COUNT_DROP);
        });
        assertThat(incidentRepository.findByStatusOrderByDetectedAtDesc(IncidentStatus.DETECTED)).hasSize(1);
        assertThat(metricSnapshotRepository.findByIncidentOrderByMeasuredAtAsc(incident)).hasSize(1);
    }

    @Test
    void changesIncidentStatusDuringAnalysisLifecycle() {
        DetectionRule rule = detectionRuleRepository.save(new DetectionRule(
            "Required field NULL spike",
            "record_subjects",
            MetricType.NULL_RATIO,
            ThresholdType.ABOVE_RATIO,
            new BigDecimal("2.0000")
        ));
        Incident incident = incidentRepository.save(new Incident(
            "INC-20260913-002",
            rule,
            IncidentSeverity.WARNING,
            "record_subjects",
            AnomalyType.NULL_SPIKE,
            "required_field_value NULL ratio increased",
            Instant.parse("2026-09-13T00:05:00Z")
        ));

        incident.markAnalyzing();
        incident.markAnalyzed();
        incident.resolve(Instant.parse("2026-09-13T01:00:00Z"));
        Incident saved = incidentRepository.saveAndFlush(incident);

        assertThat(saved.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(saved.getResolvedAt()).isEqualTo(Instant.parse("2026-09-13T01:00:00Z"));
    }
}
