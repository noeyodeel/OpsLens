package com.opslens.application.report;

import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opslens.application.analysis.IncidentAnalysisNotFoundException;
import com.opslens.application.analysis.IncidentAnalysisService;
import com.opslens.application.analysis.IncidentAnalysisService.StoredIncidentAnalysis;
import com.opslens.application.incident.IncidentNotFoundException;
import com.opslens.domain.analysis.IncidentAnalysis;
import com.opslens.domain.analysis.IncidentAnalysisRepository;
import com.opslens.domain.analysis.IncidentReport;
import com.opslens.domain.analysis.IncidentReportRepository;
import com.opslens.domain.analysis.IncidentReportType;
import com.opslens.domain.incident.Incident;
import com.opslens.domain.incident.IncidentRepository;

@Service
public class IncidentReportService {

    private final IncidentRepository incidentRepository;
    private final IncidentAnalysisRepository incidentAnalysisRepository;
    private final IncidentReportRepository incidentReportRepository;
    private final ObjectMapper objectMapper;

    public IncidentReportService(
        IncidentRepository incidentRepository,
        IncidentAnalysisRepository incidentAnalysisRepository,
        IncidentReportRepository incidentReportRepository,
        ObjectMapper objectMapper
    ) {
        this.incidentRepository = incidentRepository;
        this.incidentAnalysisRepository = incidentAnalysisRepository;
        this.incidentReportRepository = incidentReportRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StoredIncidentReport generateReport(String incidentNo, IncidentReportType reportType) {
        Incident incident = findIncident(incidentNo);
        IncidentAnalysis analysis = incidentAnalysisRepository.findFirstByIncidentOrderByAnalyzedAtDesc(incident)
            .orElseThrow(() -> new IncidentAnalysisNotFoundException(incidentNo));
        StoredIncidentAnalysis storedAnalysis = IncidentAnalysisService.toStoredAnalysis(analysis, objectMapper);
        ReportDraft draft = reportType == IncidentReportType.DEVELOPER
            ? developerReport(incident, storedAnalysis)
            : businessReport(incident, storedAnalysis);

        IncidentReport report = incidentReportRepository.save(new IncidentReport(
            incident,
            analysis,
            reportType,
            draft.title(),
            draft.content(),
            Instant.now()
        ));
        return StoredIncidentReport.from(report);
    }

    @Transactional(readOnly = true)
    public StoredIncidentReport getLatestReport(String incidentNo, IncidentReportType reportType) {
        Incident incident = findIncident(incidentNo);
        IncidentReport report = incidentReportRepository.findFirstByIncidentAndReportTypeOrderByGeneratedAtDesc(
            incident,
            reportType
        ).orElseThrow(() -> new IncidentReportNotFoundException(incidentNo, reportType));
        return StoredIncidentReport.from(report);
    }

    private Incident findIncident(String incidentNo) {
        return incidentRepository.findByIncidentNo(incidentNo)
            .orElseThrow(() -> new IncidentNotFoundException(incidentNo));
    }

    private ReportDraft developerReport(Incident incident, StoredIncidentAnalysis analysis) {
        String causes = analysis.suspectedCauses().stream()
            .map(cause -> "- #%d %s: %s (신뢰도 %s)".formatted(
                cause.rank(),
                cause.cause(),
                cause.reason(),
                cause.confidence().stripTrailingZeros().toPlainString()
            ))
            .collect(Collectors.joining("\n"));
        String verificationSql = analysis.verificationSql().stream()
            .map(sql -> "- %s [%s]: %s\n```sql\n%s\n```".formatted(
                sql.title(),
                sql.safe() ? "안전" : "주의 필요",
                sql.purpose(),
                sql.sql()
            ))
            .collect(Collectors.joining("\n\n"));
        String checks = analysis.additionalChecks().stream()
            .map("- %s"::formatted)
            .collect(Collectors.joining("\n"));

        String content = """
            # 개발자용 인시던트 리포트

            인시던트: %s
            심각도: %s
            상태: %s
            대상 테이블: %s
            대상 기관: %s
            이상 유형: %s
            탐지 시각: %s

            ## 분석 요약
            %s

            ## 영향 범위
            %s

            ## 원인 후보
            %s

            ## 검증 SQL
            %s

            ## 추가 확인 사항
            %s
            """.formatted(
            incident.getIncidentNo(),
            incident.getSeverity(),
            incident.getStatus(),
            incident.getTargetTable(),
            institutionCode(incident),
            incident.getAnomalyType(),
            incident.getDetectedAt(),
            analysis.summary(),
            analysis.impactScope(),
            emptyFallback(causes),
            emptyFallback(verificationSql),
            emptyFallback(checks)
        ).strip();
        return new ReportDraft("개발자용 리포트 - %s".formatted(incident.getIncidentNo()), content);
    }

    private ReportDraft businessReport(Incident incident, StoredIncidentAnalysis analysis) {
        String mainCause = analysis.suspectedCauses().isEmpty()
            ? "가능성이 높은 원인을 확인 중입니다."
            : analysis.suspectedCauses().get(0).cause();
        String checks = analysis.additionalChecks().stream()
            .map("- %s"::formatted)
            .collect(Collectors.joining("\n"));
        String content = """
            # 업무 담당자용 인시던트 리포트

            ## 현재 발생한 문제
            %s

            ## 예상 업무 영향
            %s

            ## 현재 파악된 내용
            현재 가장 가능성이 높은 원인 후보는 "%s"입니다.

            ## 확인 중인 사항
            %s

            ## 현재 상태
            개발자가 원인과 영향 범위를 검토 중입니다. 운영 데이터 변경은 자동으로 수행되지 않습니다.
            """.formatted(
            analysis.summary(),
            analysis.impactScope(),
            mainCause,
            emptyFallback(checks)
        ).strip();
        return new ReportDraft("업무 담당자용 리포트 - %s".formatted(incident.getIncidentNo()), content);
    }

    private String institutionCode(Incident incident) {
        return incident.getTargetInstitutionCode() == null ? "-" : incident.getTargetInstitutionCode();
    }

    private String emptyFallback(String value) {
        return value == null || value.isBlank() ? "- 기록된 항목이 없습니다." : value;
    }

    private record ReportDraft(String title, String content) {
    }

    public record StoredIncidentReport(
        Long id,
        String incidentNo,
        Long analysisId,
        IncidentReportType reportType,
        String title,
        String content,
        Instant generatedAt
    ) {

        private static StoredIncidentReport from(IncidentReport report) {
            return new StoredIncidentReport(
                report.getId(),
                report.getIncident().getIncidentNo(),
                report.getIncidentAnalysis().getId(),
                report.getReportType(),
                report.getTitle(),
                report.getContent(),
                report.getGeneratedAt()
            );
        }
    }
}
