package com.opslens.api.incident;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opslens.application.detection.CountDropDetectionService;
import com.opslens.application.detection.CountDropDetectionService.CountDropDetectionCommand;
import com.opslens.application.detection.CountDropDetectionService.CountDropDetectionResult;

@RestController
@RequestMapping("/api/incidents")
public class IncidentDetectionController {

    private final CountDropDetectionService countDropDetectionService;

    public IncidentDetectionController(CountDropDetectionService countDropDetectionService) {
        this.countDropDetectionService = countDropDetectionService;
    }

    @PostMapping("/detect")
    public List<CountDropDetectionResult> detect(@RequestBody(required = false) DetectIncidentRequest request) {
        DetectIncidentRequest safeRequest = request == null ? DetectIncidentRequest.empty() : request;
        return countDropDetectionService.detect(safeRequest.toCommand());
    }

    public record DetectIncidentRequest(LocalDate targetDate) {

        private static DetectIncidentRequest empty() {
            return new DetectIncidentRequest(null);
        }

        private CountDropDetectionCommand toCommand() {
            return new CountDropDetectionCommand(targetDate);
        }
    }
}
