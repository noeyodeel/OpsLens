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
            .map(cause -> "- #%d %s: %s (confidence %s)".formatted(
                cause.rank(),
                cause.cause(),
                cause.reason(),
                cause.confidence().stripTrailingZeros().toPlainString()
            ))
            .collect(Collectors.joining("\n"));
        String verificationSql = analysis.verificationSql().stream()
            .map(sql -> "- %s [%s]: %s\n```sql\n%s\n```".formatted(
                sql.title(),
                sql.safe() ? "SAFE" : "UNSAFE",
                sql.purpose(),
                sql.sql()
            ))
            .collect(Collectors.joining("\n\n"));
        String checks = analysis.additionalChecks().stream()
            .map("- %s"::formatted)
            .collect(Collectors.joining("\n"));

        String content = """
            # Developer Incident Report

            Incident: %s
            Severity: %s
            Status: %s
            Target table: %s
            Target institution: %s
            Anomaly type: %s
            Detected at: %s

            ## Analysis Summary
            %s

            ## Impact Scope
            %s

            ## Suspected Causes
            %s

            ## Verification SQL
            %s

            ## Additional Checks
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
        return new ReportDraft("Developer report - %s".formatted(incident.getIncidentNo()), content);
    }

    private ReportDraft businessReport(Incident incident, StoredIncidentAnalysis analysis) {
        String mainCause = analysis.suspectedCauses().isEmpty()
            ? "The likely cause is still being checked."
            : analysis.suspectedCauses().get(0).cause();
        String checks = analysis.additionalChecks().stream()
            .map("- %s"::formatted)
            .collect(Collectors.joining("\n"));
        String content = """
            # Business Incident Report

            ## What Happened
            %s

            ## Expected Business Impact
            %s

            ## Current Understanding
            The current top candidate is: %s.

            ## What Is Being Checked
            %s

            ## Current Status
            The incident is under developer review. No production data changes are performed automatically.
            """.formatted(
            analysis.summary(),
            analysis.impactScope(),
            mainCause,
            emptyFallback(checks)
        ).strip();
        return new ReportDraft("Business report - %s".formatted(incident.getIncidentNo()), content);
    }

    private String institutionCode(Incident incident) {
        return incident.getTargetInstitutionCode() == null ? "-" : incident.getTargetInstitutionCode();
    }

    private String emptyFallback(String value) {
        return value == null || value.isBlank() ? "- No item recorded." : value;
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
