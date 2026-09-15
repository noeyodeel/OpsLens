package com.opslens.application.detection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.opslens.application.detection.RequiredFieldNullSpikeDetectionService.RequiredFieldNullSpikeDetectionCommand;
import com.opslens.application.detection.RequiredFieldNullSpikeDetectionService.RequiredFieldNullSpikeDetectionResult;
import com.opslens.application.scenario.ScenarioInjectionService;
import com.opslens.application.scenario.ScenarioInjectionService.ScenarioInjectionCommand;
import com.opslens.application.testdata.TestDataGenerationService;
import com.opslens.application.testdata.TestDataGenerationService.TestDataGenerationCommand;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.incident.Incident;
import com.opslens.domain.incident.IncidentMetricSnapshotRepository;
import com.opslens.domain.incident.IncidentRepository;
import com.opslens.domain.incident.IncidentSeverity;
import com.opslens.domain.incident.IncidentStatus;
import com.opslens.domain.scenario.ScenarioType;

@DataJpaTest(showSql = false)
@Import({
    TestDataGenerationService.class,
    ScenarioInjectionService.class,
    RequiredFieldNullSpikeDetectionService.class
})
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RequiredFieldNullSpikeDetectionServiceTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 9, 13);

    @Autowired
    private TestDataGenerationService testDataGenerationService;

    @Autowired
    private ScenarioInjectionService scenarioInjectionService;

    @Autowired
    private RequiredFieldNullSpikeDetectionService requiredFieldNullSpikeDetectionService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentMetricSnapshotRepository metricSnapshotRepository;

    @BeforeEach
    void setUp() {
        testDataGenerationService.generate(new TestDataGenerationCommand(
            8,
            3,
            10,
            40,
            20260913L,
            TARGET_DATE,
            true
        ));
    }

    @Test
    void createsIncidentWhenRequiredFieldNullRatioExceedsThreshold() {
        scenarioInjectionService.inject(new ScenarioInjectionCommand(
            ScenarioType.REQUIRED_FIELD_NULL_SPIKE,
            "INST_02",
            TARGET_DATE
        ));

        var results = requiredFieldNullSpikeDetectionService.detect(new RequiredFieldNullSpikeDetectionCommand(TARGET_DATE));

        assertThat(results).hasSize(3);
        assertThat(results)
            .filteredOn(RequiredFieldNullSpikeDetectionResult::incidentCreated)
            .singleElement()
            .satisfies(result -> {
                assertThat(result.targetInstitutionCode()).isEqualTo("INST_02");
                assertThat(result.baselineNullRatio()).isEqualByComparingTo("0.0000");
                assertThat(result.currentNullRatio()).isEqualByComparingTo("80.0000");
                assertThat(result.changeRate()).isEqualByComparingTo("80.0000");
                assertThat(result.totalCount()).isEqualTo(10);
                assertThat(result.nullCount()).isEqualTo(8);
                assertThat(result.incidentNo()).isEqualTo("INC-20260913-NULL-SPIKE-INST_02");
            });

        Incident incident = incidentRepository.findByIncidentNo("INC-20260913-NULL-SPIKE-INST_02").orElseThrow();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.DETECTED);
        assertThat(incident.getSeverity()).isEqualTo(IncidentSeverity.WARNING);
        assertThat(incident.getAnomalyType()).isEqualTo(AnomalyType.NULL_SPIKE);
        assertThat(incident.getTargetInstitutionCode()).isEqualTo("INST_02");
        assertThat(metricSnapshotRepository.findByIncidentOrderByMeasuredAtAsc(incident)).hasSize(1);
    }

    @Test
    void doesNotCreateDuplicateIncidentForSameInstitutionAndDate() {
        scenarioInjectionService.inject(new ScenarioInjectionCommand(
            ScenarioType.REQUIRED_FIELD_NULL_SPIKE,
            "INST_02",
            TARGET_DATE
        ));

        requiredFieldNullSpikeDetectionService.detect(new RequiredFieldNullSpikeDetectionCommand(TARGET_DATE));
        requiredFieldNullSpikeDetectionService.detect(new RequiredFieldNullSpikeDetectionCommand(TARGET_DATE));

        assertThat(incidentRepository.count()).isEqualTo(1);
        Incident incident = incidentRepository.findByIncidentNo("INC-20260913-NULL-SPIKE-INST_02").orElseThrow();
        assertThat(metricSnapshotRepository.findByIncidentOrderByMeasuredAtAsc(incident)).hasSize(1);
    }

    @Test
    void doesNotCreateIncidentForNormalData() {
        var results = requiredFieldNullSpikeDetectionService.detect(new RequiredFieldNullSpikeDetectionCommand(TARGET_DATE));

        assertThat(results).hasSize(3);
        assertThat(results).noneMatch(RequiredFieldNullSpikeDetectionResult::incidentCreated);
        assertThat(incidentRepository.count()).isZero();
    }
}
