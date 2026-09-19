package com.opslens.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opslens.application.analysis.AiIncidentContextBuilderService.AiIncidentContext;
import com.opslens.application.analysis.IncidentAnalysisClient.IncidentAnalysisResult;
import com.opslens.application.analysis.IncidentAnalysisClient.SuspectedCause;
import com.opslens.application.analysis.IncidentAnalysisClient.VerificationSql;
import com.opslens.domain.analysis.IncidentAnalysis;
import com.opslens.domain.analysis.IncidentAnalysisRepository;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.incident.Incident;
import com.opslens.domain.incident.IncidentRepository;
import com.opslens.domain.incident.IncidentSeverity;

@ExtendWith(MockitoExtension.class)
class IncidentAnalysisServiceTest {

    @Mock
    private AiIncidentContextBuilderService contextBuilderService;

    @Mock
    private IncidentAnalysisClient incidentAnalysisClient;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentAnalysisRepository incidentAnalysisRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsContextDelegatesToAnalysisClientAndStoresResult() {
        IncidentAnalysisService incidentAnalysisService = new IncidentAnalysisService(
            contextBuilderService,
            incidentAnalysisClient,
            incidentRepository,
            incidentAnalysisRepository,
            objectMapper,
            new SqlSafetyValidator()
        );
        Incident incident = new Incident(
            "INC-1",
            null,
            IncidentSeverity.CRITICAL,
            "treatment_records",
            "INST_02",
            AnomalyType.SOURCE_MISSING,
            "summary",
            Instant.parse("2026-09-14T00:00:00Z")
        );
        AiIncidentContext context = new AiIncidentContext(null, List.of(), List.of(), List.of(), List.of());
        IncidentAnalysisResult expected = new IncidentAnalysisResult(
            "summary",
            "impact",
            List.of(new SuspectedCause(1, "cause", "reason", new BigDecimal("0.8000"))),
            List.of(new VerificationSql("title", "purpose", "select 1")),
            List.of("check"),
            true
        );
        when(incidentRepository.findByIncidentNo("INC-1")).thenReturn(Optional.of(incident));
        when(contextBuilderService.buildContext("INC-1")).thenReturn(context);
        when(incidentAnalysisClient.analyze(context)).thenReturn(expected);
        when(incidentAnalysisRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = incidentAnalysisService.analyze("INC-1");

        assertThat(result.incidentNo()).isEqualTo("INC-1");
        assertThat(result.summary()).isEqualTo("summary");
        assertThat(result.suspectedCauses()).singleElement()
            .satisfies(cause -> assertThat(cause.cause()).isEqualTo("cause"));
        assertThat(result.verificationSql()).singleElement()
            .satisfies(sql -> {
                assertThat(sql.sql()).isEqualTo("select 1");
                assertThat(sql.safe()).isTrue();
                assertThat(sql.safetyMessage()).isEqualTo("조회 전용 검증 SQL입니다.");
            });
        assertThat(incident.getStatus().name()).isEqualTo("ANALYZED");
        verify(contextBuilderService).buildContext("INC-1");
        verify(incidentAnalysisClient).analyze(context);
    }
}
