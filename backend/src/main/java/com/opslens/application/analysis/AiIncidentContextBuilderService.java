package com.opslens.application.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opslens.application.incident.IncidentNotFoundException;
import com.opslens.domain.datasource.DataIngestionLog;
import com.opslens.domain.datasource.DataIngestionLogRepository;
import com.opslens.domain.datasource.ExternalInstitution;
import com.opslens.domain.datasource.ExternalInstitutionRepository;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.incident.DetectionRule;
import com.opslens.domain.incident.Incident;
import com.opslens.domain.incident.IncidentMetricSnapshot;
import com.opslens.domain.incident.IncidentMetricSnapshotRepository;
import com.opslens.domain.incident.IncidentRepository;
import com.opslens.domain.incident.IncidentSeverity;
import com.opslens.domain.incident.IncidentStatus;
import com.opslens.domain.incident.MetricType;
import com.opslens.domain.incident.ThresholdType;

@Service
public class AiIncidentContextBuilderService {

    private static final Pattern INCIDENT_DATE_PATTERN = Pattern.compile("^INC-(\\d{8})-.*$");
    private static final List<String> SCHEMA_HINTS = List.of(
        "treatment_records.external_institution_id references external_institution.id",
        "data_ingestion_log tracks received, success, failed counts by institution, target table, and batch date",
        "incident_metric_snapshot stores baseline, current, and change rate values captured when detection created an incident"
    );
    private static final List<String> SAFETY_RULES = List.of(
        "AI receives this bounded context only and must not connect directly to PostgreSQL",
        "Generated SQL must be SELECT-only and used for verification",
        "UPDATE, DELETE, INSERT, DROP, ALTER, TRUNCATE, CREATE, and MERGE statements are not allowed",
        "Final cause, impact, and remediation decisions remain with a human operator"
    );

    private final IncidentRepository incidentRepository;
    private final IncidentMetricSnapshotRepository metricSnapshotRepository;
    private final ExternalInstitutionRepository externalInstitutionRepository;
    private final DataIngestionLogRepository dataIngestionLogRepository;

    public AiIncidentContextBuilderService(
        IncidentRepository incidentRepository,
        IncidentMetricSnapshotRepository metricSnapshotRepository,
        ExternalInstitutionRepository externalInstitutionRepository,
        DataIngestionLogRepository dataIngestionLogRepository
    ) {
        this.incidentRepository = incidentRepository;
        this.metricSnapshotRepository = metricSnapshotRepository;
        this.externalInstitutionRepository = externalInstitutionRepository;
        this.dataIngestionLogRepository = dataIngestionLogRepository;
    }

    @Transactional(readOnly = true)
    public AiIncidentContext buildContext(String incidentNo) {
        Incident incident = incidentRepository.findByIncidentNo(incidentNo)
            .orElseThrow(() -> new IncidentNotFoundException(incidentNo));
        List<MetricEvidence> relatedMetrics = metricSnapshotRepository.findByIncidentOrderByMeasuredAtAsc(incident).stream()
            .map(MetricEvidence::from)
            .toList();
        LocalDate analysisDate = analysisDate(incident);
        List<IngestionEvidence> ingestionLogs = ingestionLogs(incident, analysisDate).stream()
            .map(IngestionEvidence::from)
            .toList();

        return new AiIncidentContext(
            IncidentFacts.from(incident, analysisDate),
            relatedMetrics,
            ingestionLogs,
            SCHEMA_HINTS,
            SAFETY_RULES
        );
    }

    private LocalDate analysisDate(Incident incident) {
        Matcher matcher = INCIDENT_DATE_PATTERN.matcher(incident.getIncidentNo());
        if (matcher.matches()) {
            return LocalDate.parse(matcher.group(1), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        }
        return LocalDate.ofInstant(incident.getDetectedAt(), ZoneOffset.UTC);
    }

    private List<DataIngestionLog> ingestionLogs(Incident incident, LocalDate analysisDate) {
        String institutionCode = incident.getTargetInstitutionCode();
        if (institutionCode != null && !institutionCode.isBlank()) {
            return externalInstitutionRepository.findByCode(institutionCode)
                .map(institution -> dataIngestionLogRepository.findByExternalInstitutionAndTargetTableAndBatchDate(
                    institution,
                    incident.getTargetTable(),
                    analysisDate
                ))
                .orElseGet(List::of);
        }
        return dataIngestionLogRepository.findByTargetTableAndBatchDate(incident.getTargetTable(), analysisDate);
    }

    public record AiIncidentContext(
        IncidentFacts incident,
        List<MetricEvidence> relatedMetrics,
        List<IngestionEvidence> ingestionLogs,
        List<String> schemaHints,
        List<String> safetyRules
    ) {
    }

    public record IncidentFacts(
        Long id,
        String incidentNo,
        IncidentSeverity severity,
        IncidentStatus status,
        String targetTable,
        String targetInstitutionCode,
        AnomalyType anomalyType,
        String summary,
        LocalDate analysisDate,
        Instant detectedAt,
        Instant resolvedAt,
        String detectionRuleName,
        MetricType metricType,
        ThresholdType thresholdType,
        BigDecimal thresholdValue
    ) {

        private static IncidentFacts from(Incident incident, LocalDate analysisDate) {
            DetectionRule rule = incident.getDetectionRule();
            return new IncidentFacts(
                incident.getId(),
                incident.getIncidentNo(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getTargetTable(),
                incident.getTargetInstitutionCode(),
                incident.getAnomalyType(),
                incident.getSummary(),
                analysisDate,
                incident.getDetectedAt(),
                incident.getResolvedAt(),
                rule == null ? null : rule.getName(),
                rule == null ? null : rule.getMetricType(),
                rule == null ? null : rule.getThresholdType(),
                rule == null ? null : rule.getThresholdValue()
            );
        }
    }

    public record MetricEvidence(
        String metricName,
        BigDecimal baselineValue,
        BigDecimal currentValue,
        BigDecimal changeRate,
        Instant measuredAt
    ) {

        private static MetricEvidence from(IncidentMetricSnapshot metricSnapshot) {
            return new MetricEvidence(
                metricSnapshot.getMetricName(),
                metricSnapshot.getBaselineValue(),
                metricSnapshot.getCurrentValue(),
                metricSnapshot.getChangeRate(),
                metricSnapshot.getMeasuredAt()
            );
        }
    }

    public record IngestionEvidence(
        String institutionCode,
        String institutionName,
        String targetTable,
        LocalDate batchDate,
        int receivedCount,
        int successCount,
        int failedCount,
        String status,
        Instant startedAt,
        Instant endedAt
    ) {

        private static IngestionEvidence from(DataIngestionLog ingestionLog) {
            ExternalInstitution institution = ingestionLog.getExternalInstitution();
            return new IngestionEvidence(
                institution.getCode(),
                institution.getName(),
                ingestionLog.getTargetTable(),
                ingestionLog.getBatchDate(),
                ingestionLog.getReceivedCount(),
                ingestionLog.getSuccessCount(),
                ingestionLog.getFailedCount(),
                ingestionLog.getStatus(),
                ingestionLog.getStartedAt(),
                ingestionLog.getEndedAt()
            );
        }
    }
}
