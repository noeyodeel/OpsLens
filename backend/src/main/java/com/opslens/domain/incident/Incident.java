package com.opslens.domain.incident;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String incidentNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detection_rule_id")
    private DetectionRule detectionRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentStatus status;

    @Column(nullable = false, length = 50)
    private String targetTable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AnomalyType anomalyType;

    @Column(nullable = false, length = 300)
    private String summary;

    @Column(nullable = false)
    private Instant detectedAt;

    private Instant resolvedAt;

    protected Incident() {
    }

    public Incident(
        String incidentNo,
        DetectionRule detectionRule,
        IncidentSeverity severity,
        String targetTable,
        AnomalyType anomalyType,
        String summary,
        Instant detectedAt
    ) {
        this.incidentNo = incidentNo;
        this.detectionRule = detectionRule;
        this.severity = severity;
        this.status = IncidentStatus.DETECTED;
        this.targetTable = targetTable;
        this.anomalyType = anomalyType;
        this.summary = summary;
        this.detectedAt = detectedAt;
    }

    public void markAnalyzing() {
        this.status = IncidentStatus.ANALYZING;
    }

    public void markAnalyzed() {
        this.status = IncidentStatus.ANALYZED;
    }

    public void resolve(Instant resolvedAt) {
        this.status = IncidentStatus.RESOLVED;
        this.resolvedAt = resolvedAt;
    }

    public Long getId() {
        return id;
    }

    public String getIncidentNo() {
        return incidentNo;
    }

    public DetectionRule getDetectionRule() {
        return detectionRule;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public AnomalyType getAnomalyType() {
        return anomalyType;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
