package com.opslens.application.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.opslens.application.scenario.ScenarioInjectionService.ScenarioInjectionCommand;
import com.opslens.application.scenario.ScenarioInjectionService.ScenarioInjectionResult;
import com.opslens.application.testdata.TestDataGenerationService;
import com.opslens.application.testdata.TestDataGenerationService.TestDataGenerationCommand;
import com.opslens.domain.datasource.RecordSubjectRepository;
import com.opslens.domain.datasource.TreatmentRecordRepository;
import com.opslens.domain.datasource.VerificationRecordRepository;
import com.opslens.domain.datasource.ExternalInstitution;
import com.opslens.domain.datasource.ExternalInstitutionRepository;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.scenario.ScenarioRepository;
import com.opslens.domain.scenario.ScenarioType;

@DataJpaTest(showSql = false)
@Import({TestDataGenerationService.class, ScenarioInjectionService.class})
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ScenarioInjectionServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 13);

    @Autowired
    private TestDataGenerationService testDataGenerationService;

    @Autowired
    private ScenarioInjectionService scenarioInjectionService;

    @Autowired
    private ExternalInstitutionRepository externalInstitutionRepository;

    @Autowired
    private RecordSubjectRepository recordSubjectRepository;

    @Autowired
    private TreatmentRecordRepository treatmentRecordRepository;

    @Autowired
    private VerificationRecordRepository verificationRecordRepository;

    @Autowired
    private ScenarioRepository scenarioRepository;

    private ExternalInstitution institution;

    @BeforeEach
    void setUp() {
        testDataGenerationService.generate(new TestDataGenerationCommand(
            2,
            2,
            5,
            10,
            17L,
            BASE_DATE,
            true
        ));
        institution = externalInstitutionRepository.findByCode("INST_02").orElseThrow();
    }

    @Test
    void injectsTreatmentRecordVolumeDropScenario() {
        assertThat(treatmentRecordsForTargetDate()).hasSize(10);

        ScenarioInjectionResult result = scenarioInjectionService.inject(new ScenarioInjectionCommand(
            ScenarioType.TREATMENT_RECORD_VOLUME_DROP,
            "INST_02",
            BASE_DATE
        ));

        assertThat(result.scenarioType()).isEqualTo(ScenarioType.TREATMENT_RECORD_VOLUME_DROP);
        assertThat(result.expectedIncidentType()).isEqualTo(AnomalyType.COUNT_DROP);
        assertThat(result.affectedRows()).isEqualTo(8);
        assertThat(treatmentRecordsForTargetDate()).hasSize(2);
        assertThat(verificationRecordsForTargetDate()).hasSize(2);
        assertThat(scenarioRepository.count()).isEqualTo(1);
    }

    @Test
    void injectsRequiredFieldNullSpikeScenario() {
        assertThat(recordSubjectRepository.findByExternalInstitution(institution))
            .allMatch(recordSubject -> recordSubject.getRequiredFieldValue() != null);

        ScenarioInjectionResult result = scenarioInjectionService.inject(new ScenarioInjectionCommand(
            ScenarioType.REQUIRED_FIELD_NULL_SPIKE,
            "INST_02",
            BASE_DATE
        ));

        long nullPhones = recordSubjectRepository.findByExternalInstitution(institution).stream()
            .filter(recordSubject -> recordSubject.getRequiredFieldValue() == null)
            .count();

        assertThat(result.expectedIncidentType()).isEqualTo(AnomalyType.NULL_SPIKE);
        assertThat(result.affectedRows()).isEqualTo(4);
        assertThat(nullPhones).isEqualTo(4);
    }

    @Test
    void injectsDuplicateVerificationRecordIdScenario() {
        ScenarioInjectionResult result = scenarioInjectionService.inject(new ScenarioInjectionCommand(
            ScenarioType.DUPLICATE_RECORD_KEY,
            "INST_02",
            BASE_DATE
        ));

        String duplicateVerificationRecordKey = "VR-DUPLICATE-INST_02-2026-09-13";

        assertThat(result.expectedIncidentType()).isEqualTo(AnomalyType.DUPLICATE_DETECTED);
        assertThat(result.affectedRows()).isEqualTo(2);
        assertThat(verificationRecordRepository.findByVerificationRecordKey(duplicateVerificationRecordKey)).hasSize(2);
    }

    private java.util.List<com.opslens.domain.datasource.TreatmentRecord> treatmentRecordsForTargetDate() {
        return treatmentRecordRepository.findByExternalInstitutionAndRecordedAtBetween(
            institution,
            BASE_DATE.atStartOfDay().toInstant(ZoneOffset.UTC),
            BASE_DATE.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        );
    }

    private java.util.List<com.opslens.domain.datasource.VerificationRecord> verificationRecordsForTargetDate() {
        return verificationRecordRepository.findByExternalInstitutionAndVerifiedAtBetween(
            institution,
            BASE_DATE.atStartOfDay().toInstant(ZoneOffset.UTC),
            BASE_DATE.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        );
    }
}
