package com.opslens.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.opslens.application.analysis.AiIncidentContextBuilderService.AiIncidentContext;
import com.opslens.application.analysis.IncidentAnalysisClient.IncidentAnalysisResult;

@ExtendWith(MockitoExtension.class)
class IncidentAnalysisServiceTest {

    @Mock
    private AiIncidentContextBuilderService contextBuilderService;

    @Mock
    private IncidentAnalysisClient incidentAnalysisClient;

    @InjectMocks
    private IncidentAnalysisService incidentAnalysisService;

    @Test
    void buildsContextAndDelegatesToAnalysisClient() {
        AiIncidentContext context = new AiIncidentContext(null, List.of(), List.of(), List.of(), List.of());
        IncidentAnalysisResult expected = new IncidentAnalysisResult(
            "summary",
            "impact",
            List.of(),
            List.of(),
            List.of(),
            true
        );
        when(contextBuilderService.buildContext("INC-1")).thenReturn(context);
        when(incidentAnalysisClient.analyze(context)).thenReturn(expected);

        var result = incidentAnalysisService.analyze("INC-1");

        assertThat(result).isEqualTo(expected);
        verify(contextBuilderService).buildContext("INC-1");
        verify(incidentAnalysisClient).analyze(context);
    }
}
