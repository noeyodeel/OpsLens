package com.opslens.application.report;

import com.opslens.domain.analysis.IncidentReportType;

public class IncidentReportNotFoundException extends RuntimeException {

    public IncidentReportNotFoundException(String incidentNo, IncidentReportType reportType) {
        super("Incident report not found: " + incidentNo + ", type=" + reportType);
    }
}
