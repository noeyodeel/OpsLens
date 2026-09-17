package com.opslens.api.incident;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.opslens.application.analysis.IncidentAnalysisClient.IncidentAnalysisResult;
import com.opslens.application.analysis.IncidentAnalysisClient.SuspectedCause;
import com.opslens.application.analysis.IncidentAnalysisClient.VerificationSql;
import com.opslens.application.analysis.IncidentAnalysisService;
import com.opslens.application.analysis.IncidentAnalysisService.StoredIncidentAnalysis;
import com.opslens.application.incident.IncidentNotFoundException;

@WebMvcTest(IncidentAnalysisController.class)
class IncidentAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentAnalysisService incidentAnalysisService;

    @Test
    void storesAndReturnsAnalysisResult() throws Exception {
        when(incidentAnalysisService.analyze("INC-20260913-SOURCE-MISSING-INST_02"))
            .thenReturn(new StoredIncidentAnalysis(
                11L,
                "INC-20260913-SOURCE-MISSING-INST_02",
                "No treatment records were received from INST_02.",
                "Treatment records from INST_02 may be missing.",
                List.of(new SuspectedCause(
                    1,
                    "External institution transmission failure",
                    "The related ingestion log should be checked.",
                    new BigDecimal("0.8500")
                )),
                List.of(new VerificationSql(
                    "Check ingestion log",
                    "Confirm batch status.",
                    "select * from data_ingestion_log"
                )),
                List.of("Confirm whether the institution sent data."),
                true,
                Instant.parse("2026-09-17T02:00:00Z")
            ));

        mockMvc.perform(post("/api/incidents/INC-20260913-SOURCE-MISSING-INST_02/analyze"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(11))
            .andExpect(jsonPath("$.incidentNo").value("INC-20260913-SOURCE-MISSING-INST_02"))
            .andExpect(jsonPath("$.summary").value("No treatment records were received from INST_02."))
            .andExpect(jsonPath("$.impactScope").value("Treatment records from INST_02 may be missing."))
            .andExpect(jsonPath("$.suspectedCauses[0].rank").value(1))
            .andExpect(jsonPath("$.suspectedCauses[0].confidence").value(0.8500))
            .andExpect(jsonPath("$.verificationSql[0].sql").value("select * from data_ingestion_log"))
            .andExpect(jsonPath("$.additionalChecks[0]").value("Confirm whether the institution sent data."))
            .andExpect(jsonPath("$.mock").value(true))
            .andExpect(jsonPath("$.analyzedAt").value("2026-09-17T02:00:00Z"));
    }

    @Test
    void returnsLatestAnalysisResult() throws Exception {
        when(incidentAnalysisService.getLatestAnalysis("INC-20260913-SOURCE-MISSING-INST_02"))
            .thenReturn(new StoredIncidentAnalysis(
                12L,
                "INC-20260913-SOURCE-MISSING-INST_02",
                "Stored summary",
                "Stored impact",
                List.of(),
                List.of(),
                List.of(),
                true,
                Instant.parse("2026-09-17T02:10:00Z")
            ));

        mockMvc.perform(get("/api/incidents/INC-20260913-SOURCE-MISSING-INST_02/analysis"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(12))
            .andExpect(jsonPath("$.summary").value("Stored summary"));
    }

    @Test
    void returnsNotFoundForUnknownIncident() throws Exception {
        when(incidentAnalysisService.analyze("INC-UNKNOWN"))
            .thenThrow(new IncidentNotFoundException("INC-UNKNOWN"));

        mockMvc.perform(post("/api/incidents/INC-UNKNOWN/analyze"))
            .andExpect(status().isNotFound());
    }
}
