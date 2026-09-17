package com.opslens.api.incident;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opslens.application.detection.DuplicateRecordKeyDetectionService;
import com.opslens.application.detection.DuplicateRecordKeyDetectionService.DuplicateRecordKeyDetectionCommand;
import com.opslens.application.detection.DuplicateRecordKeyDetectionService.DuplicateRecordKeyDetectionResult;
import com.opslens.application.detection.RequiredFieldNullSpikeDetectionService;
import com.opslens.application.detection.RequiredFieldNullSpikeDetectionService.RequiredFieldNullSpikeDetectionCommand;
import com.opslens.application.detection.RequiredFieldNullSpikeDetectionService.RequiredFieldNullSpikeDetectionResult;
import com.opslens.application.detection.VolumeDropDetectionService;
import com.opslens.application.detection.VolumeDropDetectionService.VolumeDropDetectionCommand;
import com.opslens.application.detection.VolumeDropDetectionService.VolumeDropDetectionResult;

@RestController
@RequestMapping("/api/incidents")
public class IncidentDetectionController {

    private final VolumeDropDetectionService volumeDropDetectionService;
    private final RequiredFieldNullSpikeDetectionService requiredFieldNullSpikeDetectionService;
    private final DuplicateRecordKeyDetectionService duplicateRecordKeyDetectionService;

    public IncidentDetectionController(
        VolumeDropDetectionService volumeDropDetectionService,
        RequiredFieldNullSpikeDetectionService requiredFieldNullSpikeDetectionService,
        DuplicateRecordKeyDetectionService duplicateRecordKeyDetectionService
    ) {
        this.volumeDropDetectionService = volumeDropDetectionService;
        this.requiredFieldNullSpikeDetectionService = requiredFieldNullSpikeDetectionService;
        this.duplicateRecordKeyDetectionService = duplicateRecordKeyDetectionService;
    }

    @PostMapping("/detect")
    public List<IncidentDetectionResult> detect(@RequestBody(required = false) DetectIncidentRequest request) {
        DetectIncidentRequest safeRequest = request == null ? DetectIncidentRequest.empty() : request;
        List<IncidentDetectionResult> results = new ArrayList<>();
        volumeDropDetectionService.detect(safeRequest.toVolumeDropCommand()).stream()
            .map(IncidentDetectionResult::from)
            .forEach(results::add);
        requiredFieldNullSpikeDetectionService.detect(safeRequest.toNullSpikeCommand()).stream()
            .map(IncidentDetectionResult::from)
            .forEach(results::add);
        duplicateRecordKeyDetectionService.detect(safeRequest.toDuplicateRecordKeyCommand()).stream()
            .map(IncidentDetectionResult::from)
            .forEach(results::add);
        return results;
    }

    public record DetectIncidentRequest(LocalDate targetDate) {

        private static DetectIncidentRequest empty() {
            return new DetectIncidentRequest(null);
        }

        private VolumeDropDetectionCommand toVolumeDropCommand() {
            return new VolumeDropDetectionCommand(targetDate);
        }

        private RequiredFieldNullSpikeDetectionCommand toNullSpikeCommand() {
            return new RequiredFieldNullSpikeDetectionCommand(targetDate);
        }

        private DuplicateRecordKeyDetectionCommand toDuplicateRecordKeyCommand() {
            return new DuplicateRecordKeyDetectionCommand(targetDate);
        }
    }

    public record IncidentDetectionResult(
        String detectorType,
        String targetInstitutionCode,
        LocalDate targetDate,
        String targetTable,
        String metricName,
        java.math.BigDecimal baselineValue,
        java.math.BigDecimal currentValue,
        java.math.BigDecimal changeRate,
        boolean incidentCreated,
        String incidentNo
    ) {

        private static IncidentDetectionResult from(VolumeDropDetectionResult result) {
            return new IncidentDetectionResult(
                "VOLUME_DROP",
                result.targetInstitutionCode(),
                result.targetDate(),
                "treatment_records",
                "daily_record_count",
                result.baselineAverage(),
                result.currentCount(),
                result.changeRate(),
                result.incidentCreated(),
                result.incidentNo()
            );
        }

        private static IncidentDetectionResult from(RequiredFieldNullSpikeDetectionResult result) {
            return new IncidentDetectionResult(
                "REQUIRED_FIELD_NULL_SPIKE",
                result.targetInstitutionCode(),
                result.targetDate(),
                "record_subjects",
                "required_field_null_ratio",
                result.baselineNullRatio(),
                result.currentNullRatio(),
                result.changeRate(),
                result.incidentCreated(),
                result.incidentNo()
            );
        }

        private static IncidentDetectionResult from(DuplicateRecordKeyDetectionResult result) {
            return new IncidentDetectionResult(
                "DUPLICATE_RECORD_KEY",
                result.targetInstitutionCode(),
                result.targetDate(),
                "verification_records",
                "verification_record_key_duplicate_count",
                result.baselineDuplicateCount(),
                result.duplicateCount(),
                result.changeRate(),
                result.incidentCreated(),
                result.incidentNo()
            );
        }
    }
}
