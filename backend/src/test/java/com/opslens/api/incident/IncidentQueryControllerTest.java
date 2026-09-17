package com.opslens.api.incident;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.opslens.application.analysis.AiIncidentContextBuilderService;
import com.opslens.application.analysis.AiIncidentContextBuilderService.AiIncidentContext;
import com.opslens.application.analysis.AiIncidentContextBuilderService.IncidentFacts;
import com.opslens.application.analysis.AiIncidentContextBuilderService.IngestionEvidence;
import com.opslens.application.analysis.AiIncidentContextBuilderService.MetricEvidence;
import com.opslens.application.incident.IncidentQueryService;
import com.opslens.application.incident.IncidentQueryService.IncidentDetail;
import com.opslens.application.incident.IncidentQueryService.IncidentSummary;
import com.opslens.application.incident.IncidentQueryService.MetricSnapshot;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.incident.IncidentSeverity;
import com.opslens.domain.incident.IncidentStatus;
import com.opslens.domain.incident.MetricType;
import com.opslens.domain.incident.ThresholdType;

@WebMvcTest(IncidentQueryController.class)
class IncidentQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentQueryService incidentQueryService;

    @MockitoBean
    private AiIncidentContextBuilderService aiIncidentContextBuilderService;

    @Test
    void returnsIncidentSummaries() throws Exception {
        when(incidentQueryService.findIncidents(any()))
            .thenReturn(List.of(new IncidentSummary(
                1L,
                "INC-20260913-SOURCE-MISSING-INST_02",
                IncidentSeverity.CRITICAL,
                IncidentStatus.DETECTED,
                "treatment_records",
                "INST_02",
                AnomalyType.SOURCE_MISSING,
                "No treatment records were received from INST_02.",
                Instant.parse("2026-09-14T00:00:00Z"),
                null,
                "Institution daily data missing"
            )));

        mockMvc.perform(get("/api/incidents")
                .param("status", "DETECTED")
                .param("from", "2026-09-14")
                .param("to", "2026-09-14"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].incidentNo").value("INC-20260913-SOURCE-MISSING-INST_02"))
            .andExpect(jsonPath("$[0].severity").value("CRITICAL"))
            .andExpect(jsonPath("$[0].status").value("DETECTED"))
            .andExpect(jsonPath("$[0].targetTable").value("treatment_records"))
            .andExpect(jsonPath("$[0].targetInstitutionCode").value("INST_02"))
            .andExpect(jsonPath("$[0].anomalyType").value("SOURCE_MISSING"))
            .andExpect(jsonPath("$[0].detectedAt").value("2026-09-14T00:00:00Z"))
            .andExpect(jsonPath("$[0].detectionRuleName").value("Institution daily data missing"));
    }

    @Test
    void returnsIncidentDetail() throws Exception {
        when(incidentQueryService.getIncident("INC-20260913-SOURCE-MISSING-INST_02"))
            .thenReturn(new IncidentDetail(
                1L,
                "INC-20260913-SOURCE-MISSING-INST_02",
                IncidentSeverity.CRITICAL,
                IncidentStatus.DETECTED,
                "treatment_records",
                "INST_02",
                AnomalyType.SOURCE_MISSING,
                "No treatment records were received from INST_02.",
                Instant.parse("2026-09-14T00:00:00Z"),
                null,
                "Institution daily data missing",
                List.of(new MetricSnapshot(
                    10L,
                    "treatment_records.daily.source_missing.INST_02",
                    new BigDecimal("40.0000"),
                    new BigDecimal("0.0000"),
                    new BigDecimal("-100.0000"),
                    Instant.parse("2026-09-14T00:00:00Z")
                ))
            ));

        mockMvc.perform(get("/api/incidents/INC-20260913-SOURCE-MISSING-INST_02"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.incidentNo").value("INC-20260913-SOURCE-MISSING-INST_02"))
            .andExpect(jsonPath("$.severity").value("CRITICAL"))
            .andExpect(jsonPath("$.targetInstitutionCode").value("INST_02"))
            .andExpect(jsonPath("$.detectionRuleName").value("Institution daily data missing"))
            .andExpect(jsonPath("$.metricSnapshots[0].id").value(10))
            .andExpect(jsonPath("$.metricSnapshots[0].metricName").value("treatment_records.daily.source_missing.INST_02"))
            .andExpect(jsonPath("$.metricSnapshots[0].baselineValue").value(40.0000))
            .andExpect(jsonPath("$.metricSnapshots[0].currentValue").value(0.0000))
            .andExpect(jsonPath("$.metricSnapshots[0].changeRate").value(-100.0000));
    }

    @Test
    void returnsAiContext() throws Exception {
        when(aiIncidentContextBuilderService.buildContext("INC-20260913-SOURCE-MISSING-INST_02"))
            .thenReturn(new AiIncidentContext(
                new IncidentFacts(
                    1L,
                    "INC-20260913-SOURCE-MISSING-INST_02",
                    IncidentSeverity.CRITICAL,
                    IncidentStatus.DETECTED,
                    "treatment_records",
                    "INST_02",
                    AnomalyType.SOURCE_MISSING,
                    "No treatment records were received from INST_02.",
                    java.time.LocalDate.of(2026, 9, 13),
                    Instant.parse("2026-09-14T00:00:00Z"),
                    null,
                    "Institution daily data missing",
                    MetricType.SOURCE_COUNT,
                    ThresholdType.EQUALS_ZERO,
                    new BigDecimal("0.0000")
                ),
                List.of(new MetricEvidence(
                    "treatment_records.daily.source_missing.INST_02",
                    new BigDecimal("40.0000"),
                    new BigDecimal("0.0000"),
                    new BigDecimal("-100.0000"),
                    Instant.parse("2026-09-14T00:00:00Z")
                )),
                List.of(new IngestionEvidence(
                    "INST_02",
                    "Beta Medical Center",
                    "treatment_records",
                    java.time.LocalDate.of(2026, 9, 13),
                    0,
                    0,
                    1,
                    "FAILED",
                    Instant.parse("2026-09-13T00:00:00Z"),
                    Instant.parse("2026-09-13T00:05:00Z")
                )),
                List.of("data_ingestion_log tracks batch status"),
                List.of("Generated SQL must be SELECT-only")
            ));

        mockMvc.perform(get("/api/incidents/INC-20260913-SOURCE-MISSING-INST_02/ai-context"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.incident.incidentNo").value("INC-20260913-SOURCE-MISSING-INST_02"))
            .andExpect(jsonPath("$.incident.analysisDate").value("2026-09-13"))
            .andExpect(jsonPath("$.incident.metricType").value("SOURCE_COUNT"))
            .andExpect(jsonPath("$.relatedMetrics[0].currentValue").value(0.0000))
            .andExpect(jsonPath("$.ingestionLogs[0].institutionCode").value("INST_02"))
            .andExpect(jsonPath("$.ingestionLogs[0].status").value("FAILED"))
            .andExpect(jsonPath("$.safetyRules[0]").value("Generated SQL must be SELECT-only"));
    }

    @Test
    void returnsNotFoundForUnknownIncident() throws Exception {
        when(incidentQueryService.getIncident("INC-UNKNOWN"))
            .thenThrow(new com.opslens.application.incident.IncidentNotFoundException("INC-UNKNOWN"));

        mockMvc.perform(get("/api/incidents/INC-UNKNOWN"))
            .andExpect(status().isNotFound());
    }
}
