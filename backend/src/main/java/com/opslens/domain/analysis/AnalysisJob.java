package com.opslens.domain.analysis;

import java.time.Instant;

import com.opslens.domain.incident.Incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "analysis_job")
public class AnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String jobId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AnalysisJobStatus status;

    @Column(length = 120)
    private String requestedBy;

    @Column(length = 1000)
    private String requestText;

    @Column(length = 120)
    private String slackChannelId;

    @Column(length = 120)
    private String slackThreadTs;

    @Column(length = 1000)
    private String slackResponseUrl;

    @Column(length = 2000)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    protected AnalysisJob() {
    }

    public AnalysisJob(
        String jobId,
        Incident incident,
        String requestedBy,
        String requestText,
        String slackChannelId,
        String slackThreadTs,
        String slackResponseUrl,
        Instant createdAt
    ) {
        this.jobId = jobId;
        this.incident = incident;
        this.status = AnalysisJobStatus.PENDING;
        this.requestedBy = requestedBy;
        this.requestText = requestText;
        this.slackChannelId = slackChannelId;
        this.slackThreadTs = slackThreadTs;
        this.slackResponseUrl = slackResponseUrl;
        this.createdAt = createdAt;
    }

    public void markRunning(Instant startedAt) {
        this.status = AnalysisJobStatus.RUNNING;
        this.startedAt = startedAt;
        this.errorMessage = null;
    }

    public void markSucceeded(Instant completedAt) {
        this.status = AnalysisJobStatus.SUCCEEDED;
        this.completedAt = completedAt;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage, Instant completedAt) {
        this.status = AnalysisJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = completedAt;
    }

    public Long getId() {
        return id;
    }

    public String getJobId() {
        return jobId;
    }

    public Incident getIncident() {
        return incident;
    }

    public AnalysisJobStatus getStatus() {
        return status;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getRequestText() {
        return requestText;
    }

    public String getSlackChannelId() {
        return slackChannelId;
    }

    public String getSlackThreadTs() {
        return slackThreadTs;
    }

    public String getSlackResponseUrl() {
        return slackResponseUrl;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
