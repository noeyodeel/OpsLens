package com.opslens.application.incident;

public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(String incidentNo) {
        super("Unknown incident: " + incidentNo);
    }
}
