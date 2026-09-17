package com.opslens.application.incident;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.incident.Incident;
import com.opslens.domain.incident.IncidentMetricSnapshot;
import com.opslens.domain.incident.IncidentMetricSnapshotRepository;
import com.opslens.domain.incident.IncidentRepository;
import com.opslens.domain.incident.IncidentSeverity;
import com.opslens.domain.incident.IncidentStatus;

@Service
public class IncidentQueryService {

    private final IncidentRepository incidentRepository;
    private final IncidentMetricSnapshotRepository metricSnapshotRepository;

    public IncidentQueryService(
        IncidentRepository incidentRepository,
        IncidentMetricSnapshotRepository metricSnapshotRepository
    ) {
        this.incidentRepository = incidentRepository;
        this.metricSnapshotRepository = metricSnapshotRepository;
    }

    @Transactional(readOnly = true)
    public List<IncidentSummary> findIncidents(IncidentQuery query) {
        IncidentQuery safeQuery = query == null ? IncidentQuery.empty() : query;
        List<Incident> incidents = findByQuery(safeQuery);
        return incidents.stream()
            .map(IncidentSummary::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public IncidentDetail getIncident(String incidentNo) {
        Incident incident = incidentRepository.findByIncidentNo(incidentNo)
            .orElseThrow(() -> new IncidentNotFoundException(incidentNo));
        List<MetricSnapshot> metricSnapshots = metricSnapshotRepository.findByIncidentOrderByMeasuredAtAsc(incident).stream()
            .map(MetricSnapshot::from)
            .toList();
        return IncidentDetail.from(incident, metricSnapshots);
    }

    private List<Incident> findByQuery(IncidentQuery query) {
        if (query.hasStatus() && query.hasDateRange()) {
            return incidentRepository.findByStatusAndDetectedAtBetweenOrderByDetectedAtDesc(
                query.status(),
                startOfDay(query.from()),
                startOfDay(query.to().plusDays(1))
            );
        }
        if (query.hasStatus()) {
            return incidentRepository.findByStatusOrderByDetectedAtDesc(query.status());
        }
        if (query.hasDateRange()) {
            return incidentRepository.findByDetectedAtBetweenOrderByDetectedAtDesc(
                startOfDay(query.from()),
                startOfDay(query.to().plusDays(1))
            );
        }
        return incidentRepository.findAllByOrderByDetectedAtDesc();
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public record IncidentQuery(
        IncidentStatus status,
        LocalDate from,
        LocalDate to
    ) {

        private static IncidentQuery empty() {
            return new IncidentQuery(null, null, null);
        }

        private boolean hasStatus() {
            return status != null;
        }

        private boolean hasDateRange() {
            return from != null && to != null;
        }
    }

    public record IncidentSummary(
        Long id,
        String incidentNo,
        IncidentSeverity severity,
        IncidentStatus status,
        String targetTable,
        String targetInstitutionCode,
        AnomalyType anomalyType,
        String summary,
        Instant detectedAt,
        Instant resolvedAt,
        String detectionRuleName
    ) {

        private static IncidentSummary from(Incident incident) {
            String detectionRuleName = incident.getDetectionRule() == null
                ? null
                : incident.getDetectionRule().getName();
            return new IncidentSummary(
                incident.getId(),
                incident.getIncidentNo(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getTargetTable(),
                incident.getTargetInstitutionCode(),
                incident.getAnomalyType(),
                incident.getSummary(),
                incident.getDetectedAt(),
                incident.getResolvedAt(),
                detectionRuleName
            );
        }
    }

    public record IncidentDetail(
        Long id,
        String incidentNo,
        IncidentSeverity severity,
        IncidentStatus status,
        String targetTable,
        String targetInstitutionCode,
        AnomalyType anomalyType,
        String summary,
        Instant detectedAt,
        Instant resolvedAt,
        String detectionRuleName,
        List<MetricSnapshot> metricSnapshots
    ) {

        private static IncidentDetail from(Incident incident, List<MetricSnapshot> metricSnapshots) {
            String detectionRuleName = incident.getDetectionRule() == null
                ? null
                : incident.getDetectionRule().getName();
            return new IncidentDetail(
                incident.getId(),
                incident.getIncidentNo(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getTargetTable(),
                incident.getTargetInstitutionCode(),
                incident.getAnomalyType(),
                incident.getSummary(),
                incident.getDetectedAt(),
                incident.getResolvedAt(),
                detectionRuleName,
                metricSnapshots
            );
        }
    }

    public record MetricSnapshot(
        Long id,
        String metricName,
        BigDecimal baselineValue,
        BigDecimal currentValue,
        BigDecimal changeRate,
        Instant measuredAt
    ) {

        private static MetricSnapshot from(IncidentMetricSnapshot metricSnapshot) {
            return new MetricSnapshot(
                metricSnapshot.getId(),
                metricSnapshot.getMetricName(),
                metricSnapshot.getBaselineValue(),
                metricSnapshot.getCurrentValue(),
                metricSnapshot.getChangeRate(),
                metricSnapshot.getMeasuredAt()
            );
        }
    }
}
