package com.opslens.domain.datasource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentRecordRepository extends JpaRepository<TreatmentRecord, Long> {

    Optional<TreatmentRecord> findByTreatmentRecordNo(String treatmentRecordNo);

    long countByRecordedAtBetween(Instant from, Instant to);

    long countByExternalInstitutionAndRecordedAtBetween(ExternalInstitution externalInstitution, Instant from, Instant to);

    List<TreatmentRecord> findByExternalInstitutionAndRecordedAtBetween(ExternalInstitution externalInstitution, Instant from, Instant to);
}
