package com.opslens.application.incident;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.opslens.application.incident.IncidentQueryService.IncidentQuery;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.incident.DetectionRule;
import com.opslens.domain.incident.DetectionRuleRepository;
import com.opslens.domain.incident.Incident;
import com.opslens.domain.incident.IncidentMetricSnapshot;
import com.opslens.domain.incident.IncidentMetricSnapshotRepository;
import com.opslens.domain.incident.IncidentRepository;
import com.opslens.domain.incident.IncidentSeverity;
import com.opslens.domain.incident.IncidentStatus;
import com.opslens.domain.incident.MetricType;
import com.opslens.domain.incident.ThresholdType;

@DataJpaTest(showSql = false)
@Import(IncidentQueryService.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class IncidentQueryServiceTest {

    @Autowired
    private DetectionRuleRepository detectionRuleRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentMetricSnapshotRepository metricSnapshotRepository;

    @Autowired
    private IncidentQueryService incidentQueryService;

    private DetectionRule countDropRule;
    private DetectionRule nullSpikeRule;

    @BeforeEach
    void setUp() {
        countDropRule = detectionRuleRepository.save(new DetectionRule(
            "Treatment records daily volume drop",
            "treatment_records",
            MetricType.ROW_COUNT,
            ThresholdType.BELOW_RATIO,
            new BigDecimal("0.5000")
        ));
        nullSpikeRule = detectionRuleRepository.save(new DetectionRule(
            "Required field NULL ratio spike",
            "record_subjects",
            MetricType.NULL_RATIO,
            ThresholdType.ABOVE_RATIO,
            new BigDecimal("20.0000")
        ));

        Incident countDropIncident = incidentRepository.save(new Incident(
            "INC-20260913-VOLUME-DROP-INST_02",
            countDropRule,
            IncidentSeverity.CRITICAL,
            "treatment_records",
            "INST_02",
            AnomalyType.COUNT_DROP,
            "Treatment record count for INST_02 dropped.",
            Instant.parse("2026-09-14T00:00:00Z")
        ));
        metricSnapshotRepository.save(new IncidentMetricSnapshot(
            countDropIncident,
            "treatment_records.daily.count.INST_02",
            new BigDecimal("40.0000"),
            new BigDecimal("8.0000"),
            new BigDecimal("-80.0000"),
            Instant.parse("2026-09-14T00:00:00Z")
        ));
        Incident resolvedIncident = incidentRepository.save(new Incident(
            "INC-20260912-NULL-SPIKE-INST_03",
            nullSpikeRule,
            IncidentSeverity.WARNING,
            "record_subjects",
            "INST_03",
            AnomalyType.NULL_SPIKE,
            "Required field NULL ratio for INST_03 increased.",
            Instant.parse("2026-09-13T00:00:00Z")
        ));
        resolvedIncident.resolve(Instant.parse("2026-09-13T02:00:00Z"));
        incidentRepository.save(resolvedIncident);
    }

    @Test
    void returnsIncidentsOrderedByDetectedAtDescending() {
        var incidents = incidentQueryService.findIncidents(new IncidentQuery(null, null, null));

        assertThat(incidents).hasSize(2);
        assertThat(incidents).extracting("incidentNo")
            .containsExactly("INC-20260913-VOLUME-DROP-INST_02", "INC-20260912-NULL-SPIKE-INST_03");
        assertThat(incidents.get(0).detectionRuleName()).isEqualTo("Treatment records daily volume drop");
    }

    @Test
    void filtersByStatus() {
        var incidents = incidentQueryService.findIncidents(new IncidentQuery(IncidentStatus.RESOLVED, null, null));

        assertThat(incidents).singleElement()
            .satisfies(incident -> {
                assertThat(incident.incidentNo()).isEqualTo("INC-20260912-NULL-SPIKE-INST_03");
                assertThat(incident.status()).isEqualTo(IncidentStatus.RESOLVED);
            });
    }

    @Test
    void filtersByInclusiveDateRange() {
        var incidents = incidentQueryService.findIncidents(new IncidentQuery(
            null,
            LocalDate.of(2026, 9, 14),
            LocalDate.of(2026, 9, 14)
        ));

        assertThat(incidents).singleElement()
            .satisfies(incident -> assertThat(incident.incidentNo()).isEqualTo("INC-20260913-VOLUME-DROP-INST_02"));
    }

    @Test
    void returnsIncidentDetailWithMetricSnapshots() {
        var detail = incidentQueryService.getIncident("INC-20260913-VOLUME-DROP-INST_02");

        assertThat(detail.incidentNo()).isEqualTo("INC-20260913-VOLUME-DROP-INST_02");
        assertThat(detail.severity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(detail.targetInstitutionCode()).isEqualTo("INST_02");
        assertThat(detail.detectionRuleName()).isEqualTo("Treatment records daily volume drop");
        assertThat(detail.metricSnapshots()).singleElement()
            .satisfies(metricSnapshot -> {
                assertThat(metricSnapshot.metricName()).isEqualTo("treatment_records.daily.count.INST_02");
                assertThat(metricSnapshot.baselineValue()).isEqualByComparingTo("40.0000");
                assertThat(metricSnapshot.currentValue()).isEqualByComparingTo("8.0000");
                assertThat(metricSnapshot.changeRate()).isEqualByComparingTo("-80.0000");
            });
    }
}
