package com.opslens.application.analysis;

import java.time.Instant;

public record AnalysisJobMessage(
    String jobId,
    String incidentNo,
    String requestedBy,
    String requestText,
    String slackChannelId,
    String slackThreadTs,
    String slackResponseUrl,
    Instant createdAt
) {
}
