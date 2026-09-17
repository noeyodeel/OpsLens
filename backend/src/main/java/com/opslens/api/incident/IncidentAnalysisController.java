package com.opslens.api.incident;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opslens.application.analysis.IncidentAnalysisNotFoundException;
import com.opslens.application.analysis.IncidentAnalysisService;
import com.opslens.application.analysis.IncidentAnalysisService.StoredIncidentAnalysis;
import com.opslens.application.incident.IncidentNotFoundException;

@RestController
@RequestMapping("/api/incidents")
public class IncidentAnalysisController {

    private final IncidentAnalysisService incidentAnalysisService;

    public IncidentAnalysisController(IncidentAnalysisService incidentAnalysisService) {
        this.incidentAnalysisService = incidentAnalysisService;
    }

    @PostMapping("/{incidentNo}/analyze")
    public StoredIncidentAnalysis analyze(@PathVariable String incidentNo) {
        return incidentAnalysisService.analyze(incidentNo);
    }

    @GetMapping("/{incidentNo}/analysis")
    public StoredIncidentAnalysis analysis(@PathVariable String incidentNo) {
        return incidentAnalysisService.getLatestAnalysis(incidentNo);
    }

    @ExceptionHandler({IncidentNotFoundException.class, IncidentAnalysisNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFound() {
    }
}
