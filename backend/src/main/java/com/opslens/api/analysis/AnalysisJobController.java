package com.opslens.api.analysis;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opslens.application.analysis.AnalysisJobNotFoundException;
import com.opslens.application.analysis.AnalysisJobService;
import com.opslens.application.analysis.AnalysisJobService.AnalysisJobRequest;
import com.opslens.application.analysis.AnalysisJobService.AnalysisJobStatusUpdate;
import com.opslens.application.analysis.AnalysisJobService.AnalysisJobView;
import com.opslens.application.incident.IncidentNotFoundException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/analysis-jobs")
public class AnalysisJobController {

    private final AnalysisJobService analysisJobService;

    public AnalysisJobController(AnalysisJobService analysisJobService) {
        this.analysisJobService = analysisJobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisJobView requestAnalysis(@Valid @RequestBody CreateAnalysisJobRequest request) {
        return analysisJobService.requestAnalysis(request.toCommand());
    }

    @GetMapping("/{jobId}")
    public AnalysisJobView job(@PathVariable String jobId) {
        return analysisJobService.getJob(jobId);
    }

    @PatchMapping("/{jobId}/status")
    public AnalysisJobView updateStatus(
        @PathVariable String jobId,
        @Valid @RequestBody UpdateAnalysisJobStatusRequest request
    ) {
        return analysisJobService.updateStatus(jobId, request.toCommand());
    }

    @ExceptionHandler({IncidentNotFoundException.class, AnalysisJobNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFound() {
    }

    public record CreateAnalysisJobRequest(
        @NotBlank String incidentNo,
        String requestedBy,
        String requestText,
        String slackChannelId,
        String slackThreadTs,
        String slackResponseUrl
    ) {

        private AnalysisJobRequest toCommand() {
            return new AnalysisJobRequest(
                incidentNo,
                requestedBy,
                requestText,
                slackChannelId,
                slackThreadTs,
                slackResponseUrl
            );
        }
    }

    public record UpdateAnalysisJobStatusRequest(
        @NotNull com.opslens.domain.analysis.AnalysisJobStatus status,
        String errorMessage
    ) {

        private AnalysisJobStatusUpdate toCommand() {
            return new AnalysisJobStatusUpdate(status, errorMessage);
        }
    }
}
