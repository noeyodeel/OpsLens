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
import com.opslens.domain.datasource.TreatmentRecordRepository;
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
public class InstitutionMissingDetectionService {

    private static final String TARGET_TABLE = "treatment_records";
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.0000");
    private static final LocalDate DEFAULT_TARGET_DATE = LocalDate.of(2026, 9, 13);

    private final ExternalInstitutionRepository externalInstitutionRepository;
    private final TreatmentRecordRepository treatmentRecordRepository;
    private final DetectionRuleRepository detectionRuleRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentMetricSnapshotRepository metricSnapshotRepository;

    public InstitutionMissingDetectionService(
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
    public List<InstitutionMissingDetectionResult> detect(InstitutionMissingDetectionCommand command) {
        LocalDate targetDate = command == null || command.targetDate() == null
            ? DEFAULT_TARGET_DATE
            : command.targetDate();
        DetectionRule rule = findOrCreateRule();
        List<InstitutionMissingDetectionResult> results = new ArrayList<>();

        for (ExternalInstitution externalInstitution : externalInstitutionRepository.findAll()) {
            if (!externalInstitution.isActive()) {
                continue;
            }

            MissingMetrics metrics = missingMetrics(externalInstitution, targetDate);
            boolean missing = metrics.hasBaseline() && metrics.currentCount().signum() == 0;
            if (!missing) {
                results.add(InstitutionMissingDetectionResult.normal(externalInstitution.getCode(), targetDate, metrics));
                continue;
            }

            Incident incident = createIncidentIfAbsent(externalInstitution, targetDate, rule, metrics);
            results.add(InstitutionMissingDetectionResult.detected(
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
                MetricType.SOURCE_COUNT,
                ThresholdType.EQUALS_ZERO
            )
            .orElseGet(() -> detectionRuleRepository.save(new DetectionRule(
                "Institution daily data missing",
                TARGET_TABLE,
                MetricType.SOURCE_COUNT,
                ThresholdType.EQUALS_ZERO,
                DEFAULT_THRESHOLD
            )));
    }

    private MissingMetrics missingMetrics(ExternalInstitution externalInstitution, LocalDate targetDate) {
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

        return new MissingMetrics(baselineAverage, current, changeRate);
    }

    private Incident createIncidentIfAbsent(
        ExternalInstitution externalInstitution,
        LocalDate targetDate,
        DetectionRule rule,
        MissingMetrics metrics
    ) {
        Instant detectedAt = startOfDay(targetDate.plusDays(1));
        boolean alreadyExists = incidentRepository.existsByTargetTableAndAnomalyTypeAndTargetInstitutionCodeAndDetectedAtBetween(
            TARGET_TABLE,
            AnomalyType.SOURCE_MISSING,
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
            AnomalyType.SOURCE_MISSING,
            "No treatment records were received from %s. Baseline average was %s.".formatted(
                externalInstitution.getCode(),
                metrics.baselineAverage().stripTrailingZeros().toPlainString()
            ),
            detectedAt
        ));
        metricSnapshotRepository.save(new IncidentMetricSnapshot(
            incident,
            "treatment_records.daily.source_missing.%s".formatted(externalInstitution.getCode()),
            metrics.baselineAverage(),
            metrics.currentCount(),
            metrics.changeRate(),
            detectedAt
        ));
        return incident;
    }

    private String incidentNo(ExternalInstitution externalInstitution, LocalDate targetDate) {
        return "INC-%s-SOURCE-MISSING-%s".formatted(targetDate.toString().replace("-", ""), externalInstitution.getCode());
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public record InstitutionMissingDetectionCommand(LocalDate targetDate) {
    }

    public record InstitutionMissingDetectionResult(
        String targetInstitutionCode,
        LocalDate targetDate,
        BigDecimal baselineAverage,
        BigDecimal currentCount,
        BigDecimal changeRate,
        boolean incidentCreated,
        String incidentNo
    ) {

        private static InstitutionMissingDetectionResult normal(
            String institutionCode,
            LocalDate targetDate,
            MissingMetrics metrics
        ) {
            return new InstitutionMissingDetectionResult(
                institutionCode,
                targetDate,
                metrics.baselineAverage(),
                metrics.currentCount(),
                metrics.changeRate(),
                false,
                null
            );
        }

        private static InstitutionMissingDetectionResult detected(
            String institutionCode,
            LocalDate targetDate,
            MissingMetrics metrics,
            String incidentNo
        ) {
            return new InstitutionMissingDetectionResult(
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

    private record MissingMetrics(
        BigDecimal baselineAverage,
        BigDecimal currentCount,
        BigDecimal changeRate
    ) {

        private boolean hasBaseline() {
            return baselineAverage.signum() > 0;
        }
    }
}
