package com.opslens.domain.datasource;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationRecordRepository extends JpaRepository<VerificationRecord, Long> {

    List<VerificationRecord> findByVerificationRecordKey(String verificationRecordKey);

    long countByVerifiedAtBetween(Instant from, Instant to);

    List<VerificationRecord> findByTreatmentRecordIn(List<TreatmentRecord> treatmentRecords);

    List<VerificationRecord> findByExternalInstitutionAndVerifiedAtBetween(ExternalInstitution externalInstitution, Instant from, Instant to);
}
