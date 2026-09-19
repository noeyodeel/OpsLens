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
        assertThat(report.title()).contains("개발자용 리포트");
        assertThat(report.content()).contains("대상 테이블: treatment_records");
        assertThat(report.content()).contains("## 검증 SQL");
        assertThat(report.content()).contains("[안전]");
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
        assertThat(report.title()).contains("업무 담당자용 리포트");
        assertThat(report.content()).contains("## 현재 발생한 문제");
        assertThat(report.content()).contains("운영 데이터 변경은 자동으로 수행되지 않습니다.");
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
            "INST_02 기관의 진료 전송 데이터가 수신되지 않았습니다.",
            Instant.parse("2026-09-14T00:00:00Z")
        );
    }

    private IncidentAnalysis analysis(Incident incident) throws JsonProcessingException {
        return new IncidentAnalysis(
            incident,
            "INST_02 기관의 진료 전송 데이터가 수신되지 않았습니다.",
            "INST_02 기관 진료 데이터가 누락되었을 수 있습니다.",
            objectMapper.writeValueAsString(List.of(new SuspectedCause(
                1,
                "외부 기관 전송 실패",
                "관련 수신 로그를 확인해야 합니다.",
                new BigDecimal("0.8500")
            ))),
            objectMapper.writeValueAsString(List.of(new VerificationSql(
                "수신 로그 확인",
                "배치 상태를 확인합니다.",
                "select * from data_ingestion_log",
                true,
                "조회 전용 검증 SQL입니다."
            ))),
            objectMapper.writeValueAsString(List.of("기관이 데이터를 전송했는지 확인합니다.")),
            true,
            Instant.parse("2026-09-17T02:00:00Z")
        );
    }
}
