package com.opslens.domain.incident;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentMetricSnapshotRepository extends JpaRepository<IncidentMetricSnapshot, Long> {

    List<IncidentMetricSnapshot> findByIncidentOrderByMeasuredAtAsc(Incident incident);
}
