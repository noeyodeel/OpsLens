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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "incident_report")
public class IncidentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_analysis_id", nullable = false)
    private IncidentAnalysis incidentAnalysis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentReportType reportType;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Instant generatedAt;

    protected IncidentReport() {
    }

    public IncidentReport(
        Incident incident,
        IncidentAnalysis incidentAnalysis,
        IncidentReportType reportType,
        String title,
        String content,
        Instant generatedAt
    ) {
        this.incident = incident;
        this.incidentAnalysis = incidentAnalysis;
        this.reportType = reportType;
        this.title = title;
        this.content = content;
        this.generatedAt = generatedAt;
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public IncidentAnalysis getIncidentAnalysis() {
        return incidentAnalysis;
    }

    public IncidentReportType getReportType() {
        return reportType;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
