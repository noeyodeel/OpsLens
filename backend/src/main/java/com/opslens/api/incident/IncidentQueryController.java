package com.opslens.api.incident;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;

import com.opslens.application.analysis.AiIncidentContextBuilderService;
import com.opslens.application.analysis.AiIncidentContextBuilderService.AiIncidentContext;
import com.opslens.application.incident.IncidentQueryService;
import com.opslens.application.incident.IncidentQueryService.IncidentDetail;
import com.opslens.application.incident.IncidentQueryService.IncidentQuery;
import com.opslens.application.incident.IncidentQueryService.IncidentSummary;
import com.opslens.application.incident.IncidentNotFoundException;
import com.opslens.domain.incident.IncidentStatus;

@RestController
@RequestMapping("/api/incidents")
public class IncidentQueryController {

    private final IncidentQueryService incidentQueryService;
    private final AiIncidentContextBuilderService aiIncidentContextBuilderService;

    public IncidentQueryController(
        IncidentQueryService incidentQueryService,
        AiIncidentContextBuilderService aiIncidentContextBuilderService
    ) {
        this.incidentQueryService = incidentQueryService;
        this.aiIncidentContextBuilderService = aiIncidentContextBuilderService;
    }

    @GetMapping
    public List<IncidentSummary> incidents(
        @RequestParam(required = false) IncidentStatus status,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to
    ) {
        return incidentQueryService.findIncidents(new IncidentQuery(status, from, to));
    }

    @GetMapping("/{incidentNo}")
    public IncidentDetail incident(@PathVariable String incidentNo) {
        return incidentQueryService.getIncident(incidentNo);
    }

    @GetMapping("/{incidentNo}/ai-context")
    public AiIncidentContext aiContext(@PathVariable String incidentNo) {
        return aiIncidentContextBuilderService.buildContext(incidentNo);
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleIncidentNotFound() {
    }
}
