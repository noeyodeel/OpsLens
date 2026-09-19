package com.opslens.application.analysis;

public class AnalysisJobNotFoundException extends RuntimeException {

    public AnalysisJobNotFoundException(String jobId) {
        super("Analysis job not found: " + jobId);
    }
}
