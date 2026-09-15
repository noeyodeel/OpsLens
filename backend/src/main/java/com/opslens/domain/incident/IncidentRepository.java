package com.opslens.domain.incident;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByIncidentNo(String incidentNo);

    List<Incident> findByStatusOrderByDetectedAtDesc(IncidentStatus status);

    List<Incident> findByDetectedAtBetweenOrderByDetectedAtDesc(Instant from, Instant to);

    boolean existsByTargetTableAndAnomalyTypeAndTargetInstitutionCodeAndDetectedAtBetween(
        String targetTable,
        AnomalyType anomalyType,
        String targetInstitutionCode,
        Instant from,
        Instant to
    );
}
