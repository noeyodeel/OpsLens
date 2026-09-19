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
import com.opslens.domain.datasource.VerificationRecordRepository;
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
public class DuplicateRecordKeyDetectionService {

    private static final String TARGET_TABLE = "verification_records";
    private static final String TARGET_COLUMN = "verification_record_key";
    private static final BigDecimal BASELINE_DUPLICATE_COUNT = new BigDecimal("0.0000");
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.0000");
    private static final LocalDate DEFAULT_TARGET_DATE = LocalDate.of(2026, 9, 13);

    private final ExternalInstitutionRepository externalInstitutionRepository;
    private final VerificationRecordRepository verificationRecordRepository;
    private final DetectionRuleRepository detectionRuleRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentMetricSnapshotRepository metricSnapshotRepository;

    public DuplicateRecordKeyDetectionService(
        ExternalInstitutionRepository externalInstitutionRepository,
        VerificationRecordRepository verificationRecordRepository,
        DetectionRuleRepository detectionRuleRepository,
        IncidentRepository incidentRepository,
        IncidentMetricSnapshotRepository metricSnapshotRepository
    ) {
        this.externalInstitutionRepository = externalInstitutionRepository;
        this.verificationRecordRepository = verificationRecordRepository;
        this.detectionRuleRepository = detectionRuleRepository;
        this.incidentRepository = incidentRepository;
        this.metricSnapshotRepository = metricSnapshotRepository;
    }

    @Transactional
    public List<DuplicateRecordKeyDetectionResult> detect(DuplicateRecordKeyDetectionCommand command) {
        LocalDate targetDate = command == null || command.targetDate() == null
            ? DEFAULT_TARGET_DATE
            : command.targetDate();
        DetectionRule rule = findOrCreateRule();
        List<DuplicateRecordKeyDetectionResult> results = new ArrayList<>();

        for (ExternalInstitution externalInstitution : externalInstitutionRepository.findAll()) {
            if (!externalInstitution.isActive()) {
                continue;
            }

            DuplicateMetrics metrics = duplicateMetrics(externalInstitution, targetDate);
            boolean duplicated = metrics.duplicateCount().compareTo(rule.getThresholdValue()) > 0;
            if (!duplicated) {
                results.add(DuplicateRecordKeyDetectionResult.normal(externalInstitution.getCode(), targetDate, metrics));
                continue;
            }

            Incident incident = createIncidentIfAbsent(externalInstitution, targetDate, rule, metrics);
            results.add(DuplicateRecordKeyDetectionResult.detected(
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
                MetricType.DUPLICATE_COUNT,
                ThresholdType.GREATER_THAN
            )
            .orElseGet(() -> detectionRuleRepository.save(new DetectionRule(
                "검증 레코드 키 중복",
                TARGET_TABLE,
                MetricType.DUPLICATE_COUNT,
                ThresholdType.GREATER_THAN,
                DEFAULT_THRESHOLD
            )));
    }

    private DuplicateMetrics duplicateMetrics(ExternalInstitution externalInstitution, LocalDate targetDate) {
        long duplicateRows = verificationRecordRepository.countDuplicateRowsByExternalInstitutionAndVerifiedAtBetween(
            externalInstitution.getId(),
            startOfDay(targetDate),
            startOfDay(targetDate.plusDays(1))
        );
        BigDecimal duplicateCount = BigDecimal.valueOf(duplicateRows).setScale(4, RoundingMode.HALF_UP);
        BigDecimal changeRate = duplicateCount.subtract(BASELINE_DUPLICATE_COUNT).setScale(4, RoundingMode.HALF_UP);

        return new DuplicateMetrics(BASELINE_DUPLICATE_COUNT, duplicateCount, changeRate);
    }

    private Incident createIncidentIfAbsent(
        ExternalInstitution externalInstitution,
        LocalDate targetDate,
        DetectionRule rule,
        DuplicateMetrics metrics
    ) {
        Instant detectedAt = startOfDay(targetDate.plusDays(1));
        boolean alreadyExists = incidentRepository.existsByTargetTableAndAnomalyTypeAndTargetInstitutionCodeAndDetectedAtBetween(
            TARGET_TABLE,
            AnomalyType.DUPLICATE_DETECTED,
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
            AnomalyType.DUPLICATE_DETECTED,
            "%s 기관의 검증 레코드 키 중복 건수가 %s건 확인되었습니다.".formatted(
                externalInstitution.getCode(),
                metrics.duplicateCount().stripTrailingZeros().toPlainString()
            ),
            detectedAt
        ));
        metricSnapshotRepository.save(new IncidentMetricSnapshot(
            incident,
            "%s.%s.duplicate_count.%s".formatted(TARGET_TABLE, TARGET_COLUMN, externalInstitution.getCode()),
            metrics.baselineDuplicateCount(),
            metrics.duplicateCount(),
            metrics.changeRate(),
            detectedAt
        ));
        return incident;
    }

    private String incidentNo(ExternalInstitution externalInstitution, LocalDate targetDate) {
        return "INC-%s-DUPLICATE-KEY-%s".formatted(targetDate.toString().replace("-", ""), externalInstitution.getCode());
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public record DuplicateRecordKeyDetectionCommand(LocalDate targetDate) {
    }

    public record DuplicateRecordKeyDetectionResult(
        String targetInstitutionCode,
        LocalDate targetDate,
        BigDecimal baselineDuplicateCount,
        BigDecimal duplicateCount,
        BigDecimal changeRate,
        boolean incidentCreated,
        String incidentNo
    ) {

        private static DuplicateRecordKeyDetectionResult normal(
            String institutionCode,
            LocalDate targetDate,
            DuplicateMetrics metrics
        ) {
            return new DuplicateRecordKeyDetectionResult(
                institutionCode,
                targetDate,
                metrics.baselineDuplicateCount(),
                metrics.duplicateCount(),
                metrics.changeRate(),
                false,
                null
            );
        }

        private static DuplicateRecordKeyDetectionResult detected(
            String institutionCode,
            LocalDate targetDate,
            DuplicateMetrics metrics,
            String incidentNo
        ) {
            return new DuplicateRecordKeyDetectionResult(
                institutionCode,
                targetDate,
                metrics.baselineDuplicateCount(),
                metrics.duplicateCount(),
                metrics.changeRate(),
                true,
                incidentNo
            );
        }
    }

    private record DuplicateMetrics(
        BigDecimal baselineDuplicateCount,
        BigDecimal duplicateCount,
        BigDecimal changeRate
    ) {
    }
}
