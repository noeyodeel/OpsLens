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

import com.opslens.domain.datasource.TreatmentRecordRepository;
import com.opslens.domain.datasource.ExternalInstitution;
import com.opslens.domain.datasource.ExternalInstitutionRepository;
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
public class VolumeDropDetectionService {

    private static final String TARGET_TABLE = "treatment_records";
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.5000");
    private static final LocalDate DEFAULT_TARGET_DATE = LocalDate.of(2026, 9, 13);

    private final ExternalInstitutionRepository externalInstitutionRepository;
    private final TreatmentRecordRepository treatmentRecordRepository;
    private final DetectionRuleRepository detectionRuleRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentMetricSnapshotRepository metricSnapshotRepository;

    public VolumeDropDetectionService(
        ExternalInstitutionRepository externalInstitutionRepository,
        TreatmentRecordRepository treatmentRecordRepository,
        DetectionRuleRepository detectionRuleRepository,
        IncidentRepository incidentRepository,
        IncidentMetricSnapshotRepository metricSnapshotRepository
    ) {
        this.externalInstitutionRepository = externalInstitutionRepository;
        this.treatmentRecordRepository = treatmentRecordRepository;
        this.detectionRuleRepository = detectionRuleRepository;
        this.incidentRepository = incidentRepository;
        this.metricSnapshotRepository = metricSnapshotRepository;
    }

    @Transactional
    public List<VolumeDropDetectionResult> detect(VolumeDropDetectionCommand command) {
        LocalDate targetDate = command == null || command.targetDate() == null
            ? DEFAULT_TARGET_DATE
            : command.targetDate();
        DetectionRule rule = findOrCreateRule();
        List<VolumeDropDetectionResult> results = new ArrayList<>();

        for (ExternalInstitution externalInstitution : externalInstitutionRepository.findAll()) {
            if (!externalInstitution.isActive()) {
                continue;
            }

            CountMetrics metrics = countMetrics(externalInstitution, targetDate);
            if (!metrics.hasBaseline()) {
                results.add(VolumeDropDetectionResult.normal(externalInstitution.getCode(), targetDate, metrics));
                continue;
            }
            if (metrics.currentCount().signum() == 0) {
                results.add(VolumeDropDetectionResult.normal(externalInstitution.getCode(), targetDate, metrics));
                continue;
            }

            BigDecimal thresholdCount = metrics.baselineAverage().multiply(rule.getThresholdValue());
            boolean dropped = metrics.currentCount().compareTo(thresholdCount) < 0;
            if (!dropped) {
                results.add(VolumeDropDetectionResult.normal(externalInstitution.getCode(), targetDate, metrics));
                continue;
            }

            Incident incident = createIncidentIfAbsent(externalInstitution, targetDate, rule, metrics);
            results.add(VolumeDropDetectionResult.detected(externalInstitution.getCode(), targetDate, metrics, incident.getIncidentNo()));
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
                "진료 전송 데이터 일별 건수 급감",
                TARGET_TABLE,
                MetricType.ROW_COUNT,
                ThresholdType.BELOW_RATIO,
                DEFAULT_THRESHOLD
            )));
    }

    private CountMetrics countMetrics(ExternalInstitution externalInstitution, LocalDate targetDate) {
        long currentCount = treatmentRecordRepository.countByExternalInstitutionAndRecordedAtBetween(
            externalInstitution,
            startOfDay(targetDate),
            startOfDay(targetDate.plusDays(1))
        );
        long baselineTotal = treatmentRecordRepository.countByExternalInstitutionAndRecordedAtBetween(
            externalInstitution,
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
        ExternalInstitution externalInstitution,
        LocalDate targetDate,
        DetectionRule rule,
        CountMetrics metrics
    ) {
        Instant detectedAt = startOfDay(targetDate.plusDays(1));
        boolean alreadyExists = incidentRepository.existsByTargetTableAndAnomalyTypeAndTargetInstitutionCodeAndDetectedAtBetween(
            TARGET_TABLE,
            AnomalyType.COUNT_DROP,
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
            IncidentSeverity.CRITICAL,
            TARGET_TABLE,
            externalInstitution.getCode(),
            AnomalyType.COUNT_DROP,
            "%s 기관의 진료 전송 데이터 건수가 정상 기준 %s건에서 %s건으로 감소했습니다.".formatted(
                externalInstitution.getCode(),
                metrics.baselineAverage().stripTrailingZeros().toPlainString(),
                metrics.currentCount().stripTrailingZeros().toPlainString()
            ),
            detectedAt
        ));
        metricSnapshotRepository.save(new IncidentMetricSnapshot(
            incident,
            "treatment_records.daily.count.%s".formatted(externalInstitution.getCode()),
            metrics.baselineAverage(),
            metrics.currentCount(),
            metrics.changeRate(),
            detectedAt
        ));
        return incident;
    }

    private String incidentNo(ExternalInstitution externalInstitution, LocalDate targetDate) {
        return "INC-%s-VOLUME-DROP-%s".formatted(targetDate.toString().replace("-", ""), externalInstitution.getCode());
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public record VolumeDropDetectionCommand(LocalDate targetDate) {
    }

    public record VolumeDropDetectionResult(
        String targetInstitutionCode,
        LocalDate targetDate,
        BigDecimal baselineAverage,
        BigDecimal currentCount,
        BigDecimal changeRate,
        boolean incidentCreated,
        String incidentNo
    ) {

        private static VolumeDropDetectionResult normal(String institutionCode, LocalDate targetDate, CountMetrics metrics) {
            return new VolumeDropDetectionResult(
                institutionCode,
                targetDate,
                metrics.baselineAverage(),
                metrics.currentCount(),
                metrics.changeRate(),
                false,
                null
            );
        }

        private static VolumeDropDetectionResult detected(
            String institutionCode,
            LocalDate targetDate,
            CountMetrics metrics,
            String incidentNo
        ) {
            return new VolumeDropDetectionResult(
                institutionCode,
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
