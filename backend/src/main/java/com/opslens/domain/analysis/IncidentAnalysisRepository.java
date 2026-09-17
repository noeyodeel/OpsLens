package com.opslens.domain.analysis;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opslens.domain.incident.Incident;

public interface IncidentAnalysisRepository extends JpaRepository<IncidentAnalysis, Long> {

    Optional<IncidentAnalysis> findFirstByIncidentOrderByAnalyzedAtDesc(Incident incident);
}
