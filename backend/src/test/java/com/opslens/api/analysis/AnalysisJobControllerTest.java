package com.opslens.api.analysis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.opslens.application.analysis.AnalysisJobService;
import com.opslens.application.analysis.AnalysisJobService.AnalysisJobView;
import com.opslens.domain.analysis.AnalysisJobStatus;

@WebMvcTest(AnalysisJobController.class)
class AnalysisJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisJobService analysisJobService;

    @Test
    void createsAnalysisJob() throws Exception {
        when(analysisJobService.requestAnalysis(any()))
            .thenReturn(new AnalysisJobView(
                "JOB-ABC123",
                "INC-20260913-SOURCE-MISSING-INST_02",
                AnalysisJobStatus.PENDING,
                "slack:U123",
                "Analyze this incident",
                "C123",
                "1726700000.000100",
                "https://hooks.slack.test/response",
                null,
                Instant.parse("2026-09-19T12:00:00Z"),
                null,
                null
            ));

        mockMvc.perform(post("/api/analysis-jobs")
                .contentType("application/json")
                .content("""
                    {
                      "incidentNo": "INC-20260913-SOURCE-MISSING-INST_02",
                      "requestedBy": "slack:U123",
                      "requestText": "Analyze this incident",
                      "slackChannelId": "C123",
                      "slackThreadTs": "1726700000.000100",
                      "slackResponseUrl": "https://hooks.slack.test/response"
                    }
                    """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.jobId").value("JOB-ABC123"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.incidentNo").value("INC-20260913-SOURCE-MISSING-INST_02"));
    }

    @Test
    void getsAnalysisJob() throws Exception {
        when(analysisJobService.getJob("JOB-ABC123"))
            .thenReturn(new AnalysisJobView(
                "JOB-ABC123",
                "INC-20260913-SOURCE-MISSING-INST_02",
                AnalysisJobStatus.SUCCEEDED,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-09-19T12:00:00Z"),
                Instant.parse("2026-09-19T12:01:00Z"),
                Instant.parse("2026-09-19T12:02:00Z")
            ));

        mockMvc.perform(get("/api/analysis-jobs/JOB-ABC123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobId").value("JOB-ABC123"))
            .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void updatesAnalysisJobStatus() throws Exception {
        when(analysisJobService.updateStatus(any(), any()))
            .thenReturn(new AnalysisJobView(
                "JOB-ABC123",
                "INC-20260913-SOURCE-MISSING-INST_02",
                AnalysisJobStatus.RUNNING,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-09-19T12:00:00Z"),
                Instant.parse("2026-09-19T12:01:00Z"),
                null
            ));

        mockMvc.perform(patch("/api/analysis-jobs/JOB-ABC123/status")
                .contentType("application/json")
                .content("""
                    {
                      "status": "RUNNING"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.startedAt").value("2026-09-19T12:01:00Z"));
    }
}
