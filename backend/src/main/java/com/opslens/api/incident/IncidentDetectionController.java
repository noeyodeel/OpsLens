package com.opslens.api.incident;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opslens.application.detection.VolumeDropDetectionService;
import com.opslens.application.detection.VolumeDropDetectionService.VolumeDropDetectionCommand;
import com.opslens.application.detection.VolumeDropDetectionService.VolumeDropDetectionResult;

@RestController
@RequestMapping("/api/incidents")
public class IncidentDetectionController {

    private final VolumeDropDetectionService volumeDropDetectionService;

    public IncidentDetectionController(VolumeDropDetectionService volumeDropDetectionService) {
        this.volumeDropDetectionService = volumeDropDetectionService;
    }

    @PostMapping("/detect")
    public List<VolumeDropDetectionResult> detect(@RequestBody(required = false) DetectIncidentRequest request) {
        DetectIncidentRequest safeRequest = request == null ? DetectIncidentRequest.empty() : request;
        return volumeDropDetectionService.detect(safeRequest.toCommand());
    }

    public record DetectIncidentRequest(LocalDate targetDate) {

        private static DetectIncidentRequest empty() {
            return new DetectIncidentRequest(null);
        }

        private VolumeDropDetectionCommand toCommand() {
            return new VolumeDropDetectionCommand(targetDate);
        }
    }
}
