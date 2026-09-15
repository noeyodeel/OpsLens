package com.opslens.domain.incident;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionRuleRepository extends JpaRepository<DetectionRule, Long> {

    List<DetectionRule> findByEnabledTrue();

    List<DetectionRule> findByTargetTableAndEnabledTrue(String targetTable);

    Optional<DetectionRule> findFirstByTargetTableAndMetricTypeAndThresholdTypeAndEnabledTrue(
        String targetTable,
        MetricType metricType,
        ThresholdType thresholdType
    );
}
