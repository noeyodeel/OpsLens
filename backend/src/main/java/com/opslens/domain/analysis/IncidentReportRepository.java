package com.opslens.domain.analysis;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.opslens.domain.incident.Incident;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {

    Optional<IncidentReport> findFirstByIncidentAndReportTypeOrderByGeneratedAtDesc(
        Incident incident,
        IncidentReportType reportType
    );
}
