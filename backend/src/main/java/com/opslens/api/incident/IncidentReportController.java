package com.opslens.api.incident;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opslens.application.analysis.IncidentAnalysisNotFoundException;
import com.opslens.application.incident.IncidentNotFoundException;
import com.opslens.application.report.IncidentReportNotFoundException;
import com.opslens.application.report.IncidentReportService;
import com.opslens.application.report.IncidentReportService.StoredIncidentReport;
import com.opslens.domain.analysis.IncidentReportType;

@RestController
@RequestMapping("/api/incidents")
public class IncidentReportController {

    private final IncidentReportService incidentReportService;

    public IncidentReportController(IncidentReportService incidentReportService) {
        this.incidentReportService = incidentReportService;
    }

    @PostMapping("/{incidentNo}/reports")
    public StoredIncidentReport generateReport(
        @PathVariable String incidentNo,
        @RequestParam IncidentReportType type
    ) {
        return incidentReportService.generateReport(incidentNo, type);
    }

    @GetMapping("/{incidentNo}/reports")
    public StoredIncidentReport report(
        @PathVariable String incidentNo,
        @RequestParam IncidentReportType type
    ) {
        return incidentReportService.getLatestReport(incidentNo, type);
    }

    @ExceptionHandler({
        IncidentNotFoundException.class,
        IncidentAnalysisNotFoundException.class,
        IncidentReportNotFoundException.class
    })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFound() {
    }
}
