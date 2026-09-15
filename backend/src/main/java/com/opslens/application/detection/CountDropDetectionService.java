package com.opslens.application.detection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opslens.domain.datasource.OrderRepository;
import com.opslens.domain.datasource.SourceSystem;
import com.opslens.domain.datasource.SourceSystemRepository;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.incident.DetectionRule;
import com.opslens.domain.incident.DetectionRuleRepository;
import com.opslens.domain.incident.Incident;
import com.opslens.domain.incident.IncidentMetricSnapshot;
import com.opslens.domain.incident.IncidentMetricSnapshotRepository;
import com.opslens.domain.incident.IncidentRepository;
import com.opslens.domain.incident.IncidentSeverity;
import com.opslens.domain.incident.MetricType;
import com.opslens.domain.incident.ThresholdType;

@Service
public class CountDropDetectionService {

    private static final String TARGET_TABLE = "orders";
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.5000");
    private static final LocalDate DEFAULT_TARGET_DATE = LocalDate.of(2026, 9, 13);

    private final SourceSystemRepository sourceSystemRepository;
    private final OrderRepository orderRepository;
    private final DetectionRuleRepository detectionRuleRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentMetricSnapshotRepository metricSnapshotRepository;

    public CountDropDetectionService(
        SourceSystemRepository sourceSystemRepository,
        OrderRepository orderRepository,
        DetectionRuleRepository detectionRuleRepository,
        IncidentRepository incidentRepository,
        IncidentMetricSnapshotRepository metricSnapshotRepository
    ) {
        this.sourceSystemRepository = sourceSystemRepository;
        this.orderRepository = orderRepository;
        this.detectionRuleRepository = detectionRuleRepository;
        this.incidentRepository = incidentRepository;
        this.metricSnapshotRepository = metricSnapshotRepository;
    }

    @Transactional
    public List<CountDropDetectionResult> detect(CountDropDetectionCommand command) {
        LocalDate targetDate = command == null || command.targetDate() == null
            ? DEFAULT_TARGET_DATE
            : command.targetDate();
        DetectionRule rule = findOrCreateRule();
        List<CountDropDetectionResult> results = new ArrayList<>();

        for (SourceSystem sourceSystem : sourceSystemRepository.findAll()) {
            if (!sourceSystem.isActive()) {
                continue;
            }

            CountMetrics metrics = countMetrics(sourceSystem, targetDate);
            if (!metrics.hasBaseline()) {
                results.add(CountDropDetectionResult.normal(sourceSystem.getCode(), targetDate, metrics));
                continue;
            }

            BigDecimal thresholdCount = metrics.baselineAverage().multiply(rule.getThresholdValue());
            boolean dropped = metrics.currentCount().compareTo(thresholdCount) < 0;
            if (!dropped) {
                results.add(CountDropDetectionResult.normal(sourceSystem.getCode(), targetDate, metrics));
                continue;
            }

            Incident incident = createIncidentIfAbsent(sourceSystem, targetDate, rule, metrics);
            results.add(CountDropDetectionResult.detected(sourceSystem.getCode(), targetDate, metrics, incident.getIncidentNo()));
        }

        return results;
    }

    private DetectionRule findOrCreateRule() {
        return detectionRuleRepository
            .findFirstByTargetTableAndMetricTypeAndThresholdTypeAndEnabledTrue(
                TARGET_TABLE,
                MetricType.ROW_COUNT,
                ThresholdType.BELOW_RATIO
            )
            .orElseGet(() -> detectionRuleRepository.save(new DetectionRule(
                "Orders daily count drop",
                TARGET_TABLE,
                MetricType.ROW_COUNT,
                ThresholdType.BELOW_RATIO,
                DEFAULT_THRESHOLD
            )));
    }

    private CountMetrics countMetrics(SourceSystem sourceSystem, LocalDate targetDate) {
        long currentCount = orderRepository.countBySourceSystemAndOrderedAtBetween(
            sourceSystem,
            startOfDay(targetDate),
            startOfDay(targetDate.plusDays(1))
        );
        long baselineTotal = orderRepository.countBySourceSystemAndOrderedAtBetween(
            sourceSystem,
            startOfDay(targetDate.minusDays(7)),
            startOfDay(targetDate)
        );
        BigDecimal baselineAverage = BigDecimal.valueOf(baselineTotal)
            .divide(BigDecimal.valueOf(7), 4, RoundingMode.HALF_UP);
        BigDecimal current = BigDecimal.valueOf(currentCount).setScale(4, RoundingMode.HALF_UP);
        BigDecimal changeRate = baselineAverage.signum() == 0
            ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
            : current.subtract(baselineAverage)
                .divide(baselineAverage, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);

        return new CountMetrics(baselineAverage, current, changeRate);
    }

    private Incident createIncidentIfAbsent(
        SourceSystem sourceSystem,
        LocalDate targetDate,
        DetectionRule rule,
        CountMetrics metrics
    ) {
        Instant detectedAt = startOfDay(targetDate.plusDays(1));
        boolean alreadyExists = incidentRepository.existsByTargetTableAndAnomalyTypeAndTargetSourceCodeAndDetectedAtBetween(
            TARGET_TABLE,
            AnomalyType.COUNT_DROP,
            sourceSystem.getCode(),
            startOfDay(targetDate),
            startOfDay(targetDate.plusDays(2))
        );
        if (alreadyExists) {
            return incidentRepository.findByIncidentNo(incidentNo(sourceSystem, targetDate)).orElseThrow();
        }

        Incident incident = incidentRepository.save(new Incident(
            incidentNo(sourceSystem, targetDate),
            rule,
            IncidentSeverity.CRITICAL,
            TARGET_TABLE,
            sourceSystem.getCode(),
            AnomalyType.COUNT_DROP,
            "Order count for %s dropped from %s to %s.".formatted(
                sourceSystem.getCode(),
                metrics.baselineAverage().stripTrailingZeros().toPlainString(),
                metrics.currentCount().stripTrailingZeros().toPlainString()
            ),
            detectedAt
        ));
        metricSnapshotRepository.save(new IncidentMetricSnapshot(
            incident,
            "orders.daily.count.%s".formatted(sourceSystem.getCode()),
            metrics.baselineAverage(),
            metrics.currentCount(),
            metrics.changeRate(),
            detectedAt
        ));
        return incident;
    }

    private String incidentNo(SourceSystem sourceSystem, LocalDate targetDate) {
        return "INC-%s-COUNT-DROP-%s".formatted(targetDate.toString().replace("-", ""), sourceSystem.getCode());
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public record CountDropDetectionCommand(LocalDate targetDate) {
    }

    public record CountDropDetectionResult(
        String targetSourceCode,
        LocalDate targetDate,
        BigDecimal baselineAverage,
        BigDecimal currentCount,
        BigDecimal changeRate,
        boolean incidentCreated,
        String incidentNo
    ) {

        private static CountDropDetectionResult normal(String sourceCode, LocalDate targetDate, CountMetrics metrics) {
            return new CountDropDetectionResult(
                sourceCode,
                targetDate,
                metrics.baselineAverage(),
                metrics.currentCount(),
                metrics.changeRate(),
                false,
                null
            );
        }

        private static CountDropDetectionResult detected(
            String sourceCode,
            LocalDate targetDate,
            CountMetrics metrics,
            String incidentNo
        ) {
            return new CountDropDetectionResult(
                sourceCode,
                targetDate,
                metrics.baselineAverage(),
                metrics.currentCount(),
                metrics.changeRate(),
                true,
                incidentNo
            );
        }
    }

    private record CountMetrics(
        BigDecimal baselineAverage,
        BigDecimal currentCount,
        BigDecimal changeRate
    ) {

        private boolean hasBaseline() {
            return baselineAverage.signum() > 0;
        }
    }
}
