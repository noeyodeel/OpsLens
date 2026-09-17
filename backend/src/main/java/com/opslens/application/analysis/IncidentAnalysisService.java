package com.opslens.application.analysis;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opslens.application.analysis.IncidentAnalysisClient.IncidentAnalysisResult;

@Service
public class IncidentAnalysisService {

    private final AiIncidentContextBuilderService contextBuilderService;
    private final IncidentAnalysisClient incidentAnalysisClient;

    public IncidentAnalysisService(
        AiIncidentContextBuilderService contextBuilderService,
        IncidentAnalysisClient incidentAnalysisClient
    ) {
        this.contextBuilderService = contextBuilderService;
        this.incidentAnalysisClient = incidentAnalysisClient;
    }

    @Transactional(readOnly = true)
    public IncidentAnalysisResult analyze(String incidentNo) {
        return incidentAnalysisClient.analyze(contextBuilderService.buildContext(incidentNo));
    }
}
