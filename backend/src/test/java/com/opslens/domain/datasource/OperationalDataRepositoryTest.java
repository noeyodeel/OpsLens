package com.opslens.domain.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest(showSql = false)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OperationalDataRepositoryTest {

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
    void savesAndLoadsOperationalData() {
        ExternalInstitution externalInstitution = externalInstitutionRepository.save(new ExternalInstitution("INST_A", "Institution A"));
        Instant now = Instant.parse("2026-09-13T00:00:00Z");

        RecordSubject recordSubject = recordSubjectRepository.save(new RecordSubject(
            "SUBJ-001",
            "Test Subject",
            "REQ-0000-0000",
            externalInstitution,
            now
        ));

        TreatmentRecord treatmentRecord = treatmentRecordRepository.save(new TreatmentRecord(
            "TR-001",
            recordSubject,
            externalInstitution,
            "COMPLETED",
            new BigDecimal("12000.00"),
            now,
            now.plusSeconds(60)
        ));

        verificationRecordRepository.save(new VerificationRecord(
            "VR-001",
            treatmentRecord,
            externalInstitution,
            "VERIFIED",
            new BigDecimal("12000.00"),
            now.plusSeconds(120)
        ));

        dataIngestionLogRepository.save(new DataIngestionLog(
            externalInstitution,
            "treatment_records",
            LocalDate.of(2026, 9, 13),
            100,
            98,
            2,
            "PARTIAL_SUCCESS",
            now.minusSeconds(300),
            now.minusSeconds(120)
        ));

        assertThat(externalInstitutionRepository.findByCode("INST_A")).hasValueSatisfying(saved ->
            assertThat(saved.getName()).isEqualTo("Institution A")
        );
        assertThat(recordSubjectRepository.findBySubjectNo("SUBJ-001")).isPresent();
        assertThat(treatmentRecordRepository.countByRecordedAtBetween(now.minusSeconds(1), now.plusSeconds(1))).isEqualTo(1);
        assertThat(verificationRecordRepository.findByVerificationRecordKey("VR-001")).hasSize(1);
        assertThat(dataIngestionLogRepository.findByTargetTableAndBatchDate("treatment_records", LocalDate.of(2026, 9, 13)))
            .hasSize(1);
    }
}
