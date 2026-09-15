package com.opslens.application.testdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.opslens.application.testdata.TestDataGenerationService.TestDataGenerationCommand;
import com.opslens.application.testdata.TestDataGenerationService.TestDataGenerationResult;
import com.opslens.domain.datasource.RecordSubjectRepository;
import com.opslens.domain.datasource.DataIngestionLogRepository;
import com.opslens.domain.datasource.TreatmentRecordRepository;
import com.opslens.domain.datasource.VerificationRecordRepository;
import com.opslens.domain.datasource.ExternalInstitutionRepository;

@DataJpaTest(showSql = false)
@Import(TestDataGenerationService.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TestDataGenerationServiceTest {

    @Autowired
    private TestDataGenerationService testDataGenerationService;

    @Autowired
    private ExternalInstitutionRepository externalInstitutionRepository;

    @Autowired
    private RecordSubjectRepository recordSubjectRepository;

    @Autowired
    private TreatmentRecordRepository treatmentRecordRepository;

    @Autowired
    private VerificationRecordRepository verificationRecordRepository;

    @Autowired
    private DataIngestionLogRepository dataIngestionLogRepository;

    @Test
    void generatesNormalOperationalDataFromDefaults() {
        TestDataGenerationResult result = testDataGenerationService.generate(null);

        assertThat(result.externalInstitutions()).isEqualTo(3);
        assertThat(result.recordSubjects()).isEqualTo(60);
        assertThat(result.treatmentRecords()).isEqualTo(960);
        assertThat(result.verificationRecords()).isEqualTo(960);
        assertThat(result.ingestionLogs()).isEqualTo(24);
        assertThat(result.days()).isEqualTo(8);
        assertThat(result.seed()).isEqualTo(20260913L);
        assertThat(result.baseDate()).isEqualTo(LocalDate.of(2026, 9, 13));

        assertThat(externalInstitutionRepository.count()).isEqualTo(3);
        assertThat(recordSubjectRepository.count()).isEqualTo(60);
        assertThat(treatmentRecordRepository.count()).isEqualTo(960);
        assertThat(verificationRecordRepository.count()).isEqualTo(960);
        assertThat(dataIngestionLogRepository.count()).isEqualTo(24);
    }

    @Test
    void resetsExistingDataBeforeGeneratingAgain() {
        TestDataGenerationCommand command = new TestDataGenerationCommand(
            2,
            2,
            3,
            4,
            7L,
            LocalDate.of(2026, 9, 13),
            true
        );

        testDataGenerationService.generate(command);
        TestDataGenerationResult result = testDataGenerationService.generate(command);

        assertThat(result.externalInstitutions()).isEqualTo(2);
        assertThat(result.recordSubjects()).isEqualTo(6);
        assertThat(result.treatmentRecords()).isEqualTo(16);
        assertThat(result.verificationRecords()).isEqualTo(16);
        assertThat(result.ingestionLogs()).isEqualTo(4);

        assertThat(externalInstitutionRepository.count()).isEqualTo(2);
        assertThat(recordSubjectRepository.count()).isEqualTo(6);
        assertThat(treatmentRecordRepository.count()).isEqualTo(16);
        assertThat(verificationRecordRepository.count()).isEqualTo(16);
        assertThat(dataIngestionLogRepository.count()).isEqualTo(4);
    }
}
