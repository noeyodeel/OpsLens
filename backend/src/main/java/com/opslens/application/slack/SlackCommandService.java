package com.opslens.application.slack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.opslens.application.analysis.AnalysisJobService;
import com.opslens.application.analysis.AnalysisJobService.AnalysisJobRequest;
import com.opslens.application.analysis.AnalysisJobService.AnalysisJobView;

@Service
public class SlackCommandService {

    private static final Pattern INCIDENT_NO_PATTERN = Pattern.compile("\\bINC-[A-Z0-9_-]+\\b");

    private final AnalysisJobService analysisJobService;

    public SlackCommandService(AnalysisJobService analysisJobService) {
        this.analysisJobService = analysisJobService;
    }

    public SlackCommandResult handle(SlackCommandRequest request) {
        String incidentNo = extractIncidentNo(request.text());
        AnalysisJobView job = analysisJobService.requestAnalysis(new AnalysisJobRequest(
            incidentNo,
            requestedBy(request.userId()),
            request.text(),
            request.channelId(),
            request.threadTs(),
            request.responseUrl()
        ));
        return new SlackCommandResult(
            job.jobId(),
            job.incidentNo(),
            "ephemeral",
            "Analysis job started. Job ID: %s, Incident: %s".formatted(job.jobId(), job.incidentNo())
        );
    }

    private String extractIncidentNo(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidSlackCommandException("Slack command text is required.");
        }
        Matcher matcher = INCIDENT_NO_PATTERN.matcher(text.toUpperCase());
        if (!matcher.find()) {
            throw new InvalidSlackCommandException("Slack command must include an incident number such as INC-20260913-SOURCE-MISSING-INST_02.");
        }
        return matcher.group();
    }

    private String requestedBy(String userId) {
        return userId == null || userId.isBlank() ? "slack:unknown" : "slack:" + userId;
    }

    public record SlackCommandRequest(
        String userId,
        String channelId,
        String text,
        String responseUrl,
        String threadTs
    ) {
    }

    public record SlackCommandResult(
        String jobId,
        String incidentNo,
        String responseType,
        String text
    ) {
    }
}
