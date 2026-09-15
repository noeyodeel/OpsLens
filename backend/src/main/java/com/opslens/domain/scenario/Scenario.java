package com.opslens.domain.scenario;

import java.time.Instant;
import java.time.LocalDate;

import com.opslens.domain.incident.AnomalyType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "scenario")
public class Scenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private ScenarioType scenarioType;

    @Column(nullable = false, length = 50)
    private String targetInstitutionCode;

    @Column(nullable = false)
    private LocalDate targetDate;

    @Column(nullable = false)
    private int affectedRows;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AnomalyType expectedIncidentType;

    @Column(nullable = false)
    private String expectedCause;

    @Column(nullable = false)
    private Instant injectedAt;

    protected Scenario() {
    }

    public Scenario(
        String name,
        ScenarioType scenarioType,
        String targetInstitutionCode,
        LocalDate targetDate,
        int affectedRows,
        AnomalyType expectedIncidentType,
        String expectedCause,
        Instant injectedAt
    ) {
        this.name = name;
        this.scenarioType = scenarioType;
        this.targetInstitutionCode = targetInstitutionCode;
        this.targetDate = targetDate;
        this.affectedRows = affectedRows;
        this.expectedIncidentType = expectedIncidentType;
        this.expectedCause = expectedCause;
        this.injectedAt = injectedAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ScenarioType getScenarioType() {
        return scenarioType;
    }

    public String getTargetInstitutionCode() {
        return targetInstitutionCode;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public int getAffectedRows() {
        return affectedRows;
    }

    public AnomalyType getExpectedIncidentType() {
        return expectedIncidentType;
    }

    public String getExpectedCause() {
        return expectedCause;
    }

    public Instant getInjectedAt() {
        return injectedAt;
    }
}
