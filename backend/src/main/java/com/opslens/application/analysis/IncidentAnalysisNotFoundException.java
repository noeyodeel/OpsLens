package com.opslens.application.analysis;

public class IncidentAnalysisNotFoundException extends RuntimeException {

    public IncidentAnalysisNotFoundException(String incidentNo) {
        super("Incident analysis not found: " + incidentNo);
    }
}
