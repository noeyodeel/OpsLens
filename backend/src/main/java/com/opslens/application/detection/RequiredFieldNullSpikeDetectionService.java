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

import com.opslens.domain.datasource.ExternalInstitution;
import com.opslens.domain.datasource.ExternalInstitutionRepository;
import com.opslens.domain.datasource.RecordSubjectRepository;
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
public class RequiredFieldNullSpikeDetectionService {

    private static final String TARGET_TABLE = "record_subjects";
    private static final String TARGET_COLUMN = "required_field_value";
    private static final BigDecimal BASELINE_NULL_RATIO = new BigDecimal("0.0000");
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("20.0000");
    private static final LocalDate DEFAULT_TARGET_DATE = LocalDate.of(2026, 9, 13);

    private final ExternalInstitutionRepository externalInstitutionRepository;
    private final RecordSubjectRepository recordSubjectRepository;
    private final DetectionRuleRepository detectionRuleRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentMetricSnapshotRepository metricSnapshotRepository;

    public RequiredFieldNullSpikeDetectionService(
        ExternalInstitutionRepository externalInstitutionRepository,
        RecordSubjectRepository recordSubjectRepository,
        DetectionRuleRepository detectionRuleRepository,
        IncidentRepository incidentRepository,
        IncidentMetricSnapshotRepository metricSnapshotRepository
    ) {
        this.externalInstitutionRepository = externalInstitutionRepository;
        this.recordSubjectRepository = recordSubjectRepository;
        this.detectionRuleRepository = detectionRuleRepository;
        this.incidentRepository = incidentRepository;
        this.metricSnapshotRepository = metricSnapshotRepository;
    }

    @Transactional
    public List<RequiredFieldNullSpikeDetectionResult> detect(RequiredFieldNullSpikeDetectionCommand command) {
        LocalDate targetDate = command == null || command.targetDate() == null
            ? DEFAULT_TARGET_DATE
            : command.targetDate();
        DetectionRule rule = findOrCreateRule();
        List<RequiredFieldNullSpikeDetectionResult> results = new ArrayList<>();

        for (ExternalInstitution externalInstitution : externalInstitutionRepository.findAll()) {
            if (!externalInstitution.isActive()) {
                continue;
            }

            NullRatioMetrics metrics = nullRatioMetrics(externalInstitution);
            boolean spiked = metrics.currentNullRatio().compareTo(rule.getThresholdValue()) > 0;
            if (!spiked) {
                results.add(RequiredFieldNullSpikeDetectionResult.normal(externalInstitution.getCode(), targetDate, metrics));
                continue;
            }

            Incident incident = createIncidentIfAbsent(externalInstitution, targetDate, rule, metrics);
            results.add(RequiredFieldNullSpikeDetectionResult.detected(
                externalInstitution.getCode(),
                targetDate,
                metrics,
                incident.getIncidentNo()
            ));
        }

        return results;
    }

    private DetectionRule findOrCreateRule() {
        return detectionRuleRepository
            .findFirstByTargetTableAndMetricTypeAndThresholdTypeAndEnabledTrue(
                TARGET_TABLE,
                MetricType.NULL_RATIO,
                ThresholdType.GREATER_THAN
            )
            .orElseGet(() -> detectionRuleRepository.save(new DetectionRule(
                "Required field NULL ratio spike",
                TARGET_TABLE,
                MetricType.NULL_RATIO,
                ThresholdType.GREATER_THAN,
                DEFAULT_THRESHOLD
            )));
    }

    private NullRatioMetrics nullRatioMetrics(ExternalInstitution externalInstitution) {
        long totalCount = recordSubjectRepository.countByExternalInstitution(externalInstitution);
        long nullCount = recordSubjectRepository.countByExternalInstitutionAndRequiredFieldValueIsNull(externalInstitution);
        BigDecimal currentNullRatio = totalCount == 0
            ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(nullCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP);
        BigDecimal changeRate = currentNullRatio.subtract(BASELINE_NULL_RATIO).setScale(4, RoundingMode.HALF_UP);

        return new NullRatioMetrics(BASELINE_NULL_RATIO, currentNullRatio, changeRate, totalCount, nullCount);
    }

    private Incident createIncidentIfAbsent(
        ExternalInstitution externalInstitution,
        LocalDate targetDate,
        DetectionRule rule,
        NullRatioMetrics metrics
    ) {
        Instant detectedAt = startOfDay(targetDate.plusDays(1));
        boolean alreadyExists = incidentRepository.existsByTargetTableAndAnomalyTypeAndTargetInstitutionCodeAndDetectedAtBetween(
            TARGET_TABLE,
            AnomalyType.NULL_SPIKE,
            externalInstitution.getCode(),
            startOfDay(targetDate),
            startOfDay(targetDate.plusDays(2))
        );
        if (alreadyExists) {
            return incidentRepository.findByIncidentNo(incidentNo(externalInstitution, targetDate)).orElseThrow();
        }

        Incident incident = incidentRepository.save(new Incident(
            incidentNo(externalInstitution, targetDate),
            rule,
            IncidentSeverity.WARNING,
            TARGET_TABLE,
            externalInstitution.getCode(),
            AnomalyType.NULL_SPIKE,
            "Required field NULL ratio for %s increased to %s%%.".formatted(
                externalInstitution.getCode(),
                metrics.currentNullRatio().stripTrailingZeros().toPlainString()
            ),
            detectedAt
        ));
        metricSnapshotRepository.save(new IncidentMetricSnapshot(
            incident,
            "%s.%s.null_ratio.%s".formatted(TARGET_TABLE, TARGET_COLUMN, externalInstitution.getCode()),
            metrics.baselineNullRatio(),
            metrics.currentNullRatio(),
            metrics.changeRate(),
            detectedAt
        ));
        return incident;
    }

    private String incidentNo(ExternalInstitution externalInstitution, LocalDate targetDate) {
        return "INC-%s-NULL-SPIKE-%s".formatted(targetDate.toString().replace("-", ""), externalInstitution.getCode());
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public record RequiredFieldNullSpikeDetectionCommand(LocalDate targetDate) {
    }

    public record RequiredFieldNullSpikeDetectionResult(
        String targetInstitutionCode,
        LocalDate targetDate,
        BigDecimal baselineNullRatio,
        BigDecimal currentNullRatio,
        BigDecimal changeRate,
        long totalCount,
        long nullCount,
        boolean incidentCreated,
        String incidentNo
    ) {

        private static RequiredFieldNullSpikeDetectionResult normal(
            String institutionCode,
            LocalDate targetDate,
            NullRatioMetrics metrics
        ) {
            return new RequiredFieldNullSpikeDetectionResult(
                institutionCode,
                targetDate,
                metrics.baselineNullRatio(),
                metrics.currentNullRatio(),
                metrics.changeRate(),
                metrics.totalCount(),
                metrics.nullCount(),
                false,
                null
            );
        }

        private static RequiredFieldNullSpikeDetectionResult detected(
            String institutionCode,
            LocalDate targetDate,
            NullRatioMetrics metrics,
            String incidentNo
        ) {
            return new RequiredFieldNullSpikeDetectionResult(
                institutionCode,
                targetDate,
                metrics.baselineNullRatio(),
                metrics.currentNullRatio(),
                metrics.changeRate(),
                metrics.totalCount(),
                metrics.nullCount(),
                true,
                incidentNo
            );
        }
    }

    private record NullRatioMetrics(
        BigDecimal baselineNullRatio,
        BigDecimal currentNullRatio,
        BigDecimal changeRate,
        long totalCount,
        long nullCount
    ) {
    }
}
