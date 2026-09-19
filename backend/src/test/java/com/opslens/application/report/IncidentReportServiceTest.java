package com.opslens.application.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opslens.application.analysis.IncidentAnalysisClient.SuspectedCause;
import com.opslens.application.analysis.IncidentAnalysisClient.VerificationSql;
import com.opslens.domain.analysis.IncidentAnalysis;
import com.opslens.domain.analysis.IncidentAnalysisRepository;
import com.opslens.domain.analysis.IncidentReportRepository;
import com.opslens.domain.analysis.IncidentReportType;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.incident.Incident;
import com.opslens.domain.incident.IncidentRepository;
import com.opslens.domain.incident.IncidentSeverity;

class IncidentReportServiceTest {

    private final IncidentRepository incidentRepository = org.mockito.Mockito.mock(IncidentRepository.class);
    private final IncidentAnalysisRepository incidentAnalysisRepository = org.mockito.Mockito.mock(IncidentAnalysisRepository.class);
    private final IncidentReportRepository incidentReportRepository = org.mockito.Mockito.mock(IncidentReportRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IncidentReportService incidentReportService = new IncidentReportService(
        incidentRepository,
        incidentAnalysisRepository,
        incidentReportRepository,
        objectMapper
    );

    @Test
    void generatesDeveloperReportFromLatestAnalysis() throws Exception {
        Incident incident = incident();
        IncidentAnalysis analysis = analysis(incident);
        when(incidentRepository.findByIncidentNo(incident.getIncidentNo())).thenReturn(Optional.of(incident));
        when(incidentAnalysisRepository.findFirstByIncidentOrderByAnalyzedAtDesc(incident)).thenReturn(Optional.of(analysis));
        when(incidentReportRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var report = incidentReportService.generateReport(incident.getIncidentNo(), IncidentReportType.DEVELOPER);

        assertThat(report.reportType()).isEqualTo(IncidentReportType.DEVELOPER);
        assertThat(report.title()).contains("Developer report");
        assertThat(report.content()).contains("Target table: treatment_records");
        assertThat(report.content()).contains("## Verification SQL");
        assertThat(report.content()).contains("[SAFE]");
        assertThat(report.content()).contains("```sql");
    }

    @Test
    void generatesBusinessReportFromLatestAnalysis() throws Exception {
        Incident incident = incident();
        IncidentAnalysis analysis = analysis(incident);
        when(incidentRepository.findByIncidentNo(incident.getIncidentNo())).thenReturn(Optional.of(incident));
        when(incidentAnalysisRepository.findFirstByIncidentOrderByAnalyzedAtDesc(incident)).thenReturn(Optional.of(analysis));
        when(incidentReportRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var report = incidentReportService.generateReport(incident.getIncidentNo(), IncidentReportType.BUSINESS);

        assertThat(report.reportType()).isEqualTo(IncidentReportType.BUSINESS);
        assertThat(report.title()).contains("Business report");
        assertThat(report.content()).contains("## What Happened");
        assertThat(report.content()).contains("No production data changes are performed automatically.");
        assertThat(report.content()).doesNotContain("```sql");
    }

    private Incident incident() {
        return new Incident(
            "INC-20260913-SOURCE-MISSING-INST_02",
            null,
            IncidentSeverity.CRITICAL,
            "treatment_records",
            "INST_02",
            AnomalyType.SOURCE_MISSING,
            "No treatment records were received from INST_02.",
            Instant.parse("2026-09-14T00:00:00Z")
        );
    }

    private IncidentAnalysis analysis(Incident incident) throws JsonProcessingException {
        return new IncidentAnalysis(
            incident,
            "No treatment records were received from INST_02.",
            "Treatment records from INST_02 may be missing.",
            objectMapper.writeValueAsString(List.of(new SuspectedCause(
                1,
                "External institution transmission failure",
                "The related ingestion log should be checked.",
                new BigDecimal("0.8500")
            ))),
            objectMapper.writeValueAsString(List.of(new VerificationSql(
                "Check ingestion log",
                "Confirm batch status.",
                "select * from data_ingestion_log",
                true,
                "SELECT-only verification SQL."
            ))),
            objectMapper.writeValueAsString(List.of("Confirm whether the institution sent data.")),
            true,
            Instant.parse("2026-09-17T02:00:00Z")
        );
    }
}
