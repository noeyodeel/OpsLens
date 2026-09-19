package com.opslens.api.incident;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.opslens.application.report.IncidentReportNotFoundException;
import com.opslens.application.report.IncidentReportService;
import com.opslens.application.report.IncidentReportService.StoredIncidentReport;
import com.opslens.domain.analysis.IncidentReportType;

@WebMvcTest(IncidentReportController.class)
class IncidentReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentReportService incidentReportService;

    @Test
    void generatesReport() throws Exception {
        when(incidentReportService.generateReport("INC-1", IncidentReportType.DEVELOPER))
            .thenReturn(new StoredIncidentReport(
                1L,
                "INC-1",
                10L,
                IncidentReportType.DEVELOPER,
                "Developer report - INC-1",
                "# Developer Incident Report",
                Instant.parse("2026-09-19T02:00:00Z")
            ));

        mockMvc.perform(post("/api/incidents/INC-1/reports").param("type", "DEVELOPER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.incidentNo").value("INC-1"))
            .andExpect(jsonPath("$.analysisId").value(10))
            .andExpect(jsonPath("$.reportType").value("DEVELOPER"))
            .andExpect(jsonPath("$.title").value("Developer report - INC-1"));
    }

    @Test
    void returnsLatestReport() throws Exception {
        when(incidentReportService.getLatestReport("INC-1", IncidentReportType.BUSINESS))
            .thenReturn(new StoredIncidentReport(
                2L,
                "INC-1",
                10L,
                IncidentReportType.BUSINESS,
                "Business report - INC-1",
                "# Business Incident Report",
                Instant.parse("2026-09-19T02:10:00Z")
            ));

        mockMvc.perform(get("/api/incidents/INC-1/reports").param("type", "BUSINESS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(2))
            .andExpect(jsonPath("$.reportType").value("BUSINESS"))
            .andExpect(jsonPath("$.content").value("# Business Incident Report"));
    }

    @Test
    void returnsNotFoundWhenReportDoesNotExist() throws Exception {
        when(incidentReportService.getLatestReport("INC-UNKNOWN", IncidentReportType.DEVELOPER))
            .thenThrow(new IncidentReportNotFoundException("INC-UNKNOWN", IncidentReportType.DEVELOPER));

        mockMvc.perform(get("/api/incidents/INC-UNKNOWN/reports").param("type", "DEVELOPER"))
            .andExpect(status().isNotFound());
    }
}
