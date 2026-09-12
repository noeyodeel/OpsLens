package com.opslens.domain.incident;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "incident_metric_snapshot")
public class IncidentMetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(nullable = false, length = 100)
    private String metricName;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal baselineValue;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal currentValue;

    @Column(precision = 12, scale = 4)
    private BigDecimal changeRate;

    @Column(nullable = false)
    private Instant measuredAt;

    protected IncidentMetricSnapshot() {
    }

    public IncidentMetricSnapshot(
        Incident incident,
        String metricName,
        BigDecimal baselineValue,
        BigDecimal currentValue,
        BigDecimal changeRate,
        Instant measuredAt
    ) {
        this.incident = incident;
        this.metricName = metricName;
        this.baselineValue = baselineValue;
        this.currentValue = currentValue;
        this.changeRate = changeRate;
        this.measuredAt = measuredAt;
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public String getMetricName() {
        return metricName;
    }

    public BigDecimal getBaselineValue() {
        return baselineValue;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public BigDecimal getChangeRate() {
        return changeRate;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }
}
