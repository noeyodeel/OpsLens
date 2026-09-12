package com.opslens.domain.incident;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "detection_rule")
public class DetectionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String targetTable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MetricType metricType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ThresholdType thresholdType;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal thresholdValue;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected DetectionRule() {
    }

    public DetectionRule(
        String name,
        String targetTable,
        MetricType metricType,
        ThresholdType thresholdType,
        BigDecimal thresholdValue
    ) {
        this.name = name;
        this.targetTable = targetTable;
        this.metricType = metricType;
        this.thresholdType = thresholdType;
        this.thresholdValue = thresholdValue;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public ThresholdType getThresholdType() {
        return thresholdType;
    }

    public BigDecimal getThresholdValue() {
        return thresholdValue;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
