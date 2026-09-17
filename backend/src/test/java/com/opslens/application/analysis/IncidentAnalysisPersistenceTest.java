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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opslens.domain.analysis.IncidentAnalysisRepository;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.incident.DetectionRule;
import com.opslens.domain.incident.DetectionRuleRepository;
import com.opslens.domain.incident.Incident;
import com.opslens.domain.incident.IncidentMetricSnapshotRepository;
import com.opslens.domain.incident.IncidentRepository;
import com.opslens.domain.incident.IncidentSeverity;
import com.opslens.domain.incident.MetricType;
import com.opslens.domain.incident.ThresholdType;

@DataJpaTest(showSql = false)
@Import({
    IncidentAnalysisService.class,
    AiIncidentContextBuilderService.class,
    MockIncidentAnalysisClient.class,
    SqlSafetyValidator.class,
    ObjectMapper.class
})
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class IncidentAnalysisPersistenceTest {

    @Autowired
    private DetectionRuleRepository detectionRuleRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentMetricSnapshotRepository metricSnapshotRepository;

    @Autowired
    private IncidentAnalysisRepository incidentAnalysisRepository;

    @Autowired
    private IncidentAnalysisService incidentAnalysisService;

    private Incident incident;

    @BeforeEach
    void setUp() {
        DetectionRule rule = detectionRuleRepository.save(new DetectionRule(
            "Institution daily data missing",
            "treatment_records",
            MetricType.SOURCE_COUNT,
            ThresholdType.EQUALS_ZERO,
            new BigDecimal("0.0000")
        ));
        incident = incidentRepository.save(new Incident(
            "INC-20260913-SOURCE-MISSING-INST_02",
            rule,
            IncidentSeverity.CRITICAL,
            "treatment_records",
            "INST_02",
            AnomalyType.SOURCE_MISSING,
            "No treatment records were received from INST_02.",
            Instant.parse("2026-09-14T00:00:00Z")
        ));
    }

    @Test
    void storesAnalysisAndReturnsLatestAnalysis() {
        var stored = incidentAnalysisService.analyze("INC-20260913-SOURCE-MISSING-INST_02");

        assertThat(stored.id()).isNotNull();
        assertThat(stored.incidentNo()).isEqualTo("INC-20260913-SOURCE-MISSING-INST_02");
        assertThat(stored.summary()).contains("No treatment records");
        assertThat(stored.suspectedCauses()).isNotEmpty();
        assertThat(stored.verificationSql()).isNotEmpty();
        assertThat(stored.verificationSql()).allSatisfy(sql -> assertThat(sql.safe()).isTrue());
        assertThat(stored.mock()).isTrue();
        assertThat(incidentAnalysisRepository.findAll()).hasSize(1);
        assertThat(incidentRepository.findById(incident.getId()).orElseThrow().getStatus().name()).isEqualTo("ANALYZED");

        var latest = incidentAnalysisService.getLatestAnalysis("INC-20260913-SOURCE-MISSING-INST_02");

        assertThat(latest.id()).isEqualTo(stored.id());
        assertThat(latest.additionalChecks()).isNotEmpty();
    }
}
