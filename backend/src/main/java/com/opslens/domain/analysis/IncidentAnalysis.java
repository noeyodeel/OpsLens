package com.opslens.domain.analysis;

import java.time.Instant;

import com.opslens.domain.incident.Incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "incident_analysis")
public class IncidentAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(nullable = false, length = 1000)
    private String impactScope;

    @Column(nullable = false, columnDefinition = "text")
    private String suspectedCausesJson;

    @Column(nullable = false, columnDefinition = "text")
    private String verificationSqlJson;

    @Column(nullable = false, columnDefinition = "text")
    private String additionalChecksJson;

    @Column(nullable = false)
    private boolean mock;

    @Column(nullable = false)
    private Instant analyzedAt;

    protected IncidentAnalysis() {
    }

    public IncidentAnalysis(
        Incident incident,
        String summary,
        String impactScope,
        String suspectedCausesJson,
        String verificationSqlJson,
        String additionalChecksJson,
        boolean mock,
        Instant analyzedAt
    ) {
        this.incident = incident;
        this.summary = summary;
        this.impactScope = impactScope;
        this.suspectedCausesJson = suspectedCausesJson;
        this.verificationSqlJson = verificationSqlJson;
        this.additionalChecksJson = additionalChecksJson;
        this.mock = mock;
        this.analyzedAt = analyzedAt;
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public String getSummary() {
        return summary;
    }

    public String getImpactScope() {
        return impactScope;
    }

    public String getSuspectedCausesJson() {
        return suspectedCausesJson;
    }

    public String getVerificationSqlJson() {
        return verificationSqlJson;
    }

    public String getAdditionalChecksJson() {
        return additionalChecksJson;
    }

    public boolean isMock() {
        return mock;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }
}
