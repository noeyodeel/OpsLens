package com.opslens.application.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.opslens.application.analysis.AnalysisJobService;
import com.opslens.application.analysis.AnalysisJobService.AnalysisJobRequest;
import com.opslens.application.analysis.AnalysisJobService.AnalysisJobView;
import com.opslens.domain.analysis.AnalysisJobStatus;

@ExtendWith(MockitoExtension.class)
class SlackCommandServiceTest {

    @Mock
    private AnalysisJobService analysisJobService;

    @InjectMocks
    private SlackCommandService slackCommandService;

    @Test
    void createsAnalysisJobFromIncidentNumberInText() {
        when(analysisJobService.requestAnalysis(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new AnalysisJobView(
                "JOB-ABC123",
                "INC-20260913-SOURCE-MISSING-INST_02",
                AnalysisJobStatus.PENDING,
                "slack:U123",
                "analyze INC-20260913-SOURCE-MISSING-INST_02",
                "C123",
                "1726700000.000100",
                "https://hooks.slack.test/response",
                null,
                Instant.parse("2026-09-19T12:00:00Z"),
                null,
                null
            ));

        var result = slackCommandService.handle(new SlackCommandService.SlackCommandRequest(
            "U123",
            "C123",
            "analyze INC-20260913-SOURCE-MISSING-INST_02",
            "https://hooks.slack.test/response",
            "1726700000.000100"
        ));

        ArgumentCaptor<AnalysisJobRequest> requestCaptor = captor();
        verify(analysisJobService).requestAnalysis(requestCaptor.capture());
        AnalysisJobRequest request = requestCaptor.getValue();

        assertThat(request.incidentNo()).isEqualTo("INC-20260913-SOURCE-MISSING-INST_02");
        assertThat(request.requestedBy()).isEqualTo("slack:U123");
        assertThat(request.slackChannelId()).isEqualTo("C123");
        assertThat(result.text()).contains("JOB-ABC123");
        assertThat(result.text()).startsWith("Analysis job started.");
    }

    @Test
    void rejectsTextWithoutIncidentNumber() {
        assertThatThrownBy(() -> slackCommandService.handle(new SlackCommandService.SlackCommandRequest(
            "U123",
            "C123",
            "analyze latest incident",
            null,
            null
        )))
            .isInstanceOf(InvalidSlackCommandException.class)
            .hasMessageContaining("incident number");
    }
}
