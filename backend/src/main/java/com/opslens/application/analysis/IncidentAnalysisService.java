package com.opslens.application.analysis;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opslens.application.analysis.IncidentAnalysisClient.IncidentAnalysisResult;
import com.opslens.application.analysis.IncidentAnalysisClient.SuspectedCause;
import com.opslens.application.analysis.IncidentAnalysisClient.VerificationSql;
import com.opslens.application.incident.IncidentNotFoundException;
import com.opslens.domain.analysis.IncidentAnalysis;
import com.opslens.domain.analysis.IncidentAnalysisRepository;
import com.opslens.domain.incident.Incident;
import com.opslens.domain.incident.IncidentRepository;

@Service
public class IncidentAnalysisService {

    private static final TypeReference<List<SuspectedCause>> SUSPECTED_CAUSE_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<VerificationSql>> VERIFICATION_SQL_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final AiIncidentContextBuilderService contextBuilderService;
    private final IncidentAnalysisClient incidentAnalysisClient;
    private final IncidentRepository incidentRepository;
    private final IncidentAnalysisRepository incidentAnalysisRepository;
    private final ObjectMapper objectMapper;
    private final SqlSafetyValidator sqlSafetyValidator;

    public IncidentAnalysisService(
        AiIncidentContextBuilderService contextBuilderService,
        IncidentAnalysisClient incidentAnalysisClient,
        IncidentRepository incidentRepository,
        IncidentAnalysisRepository incidentAnalysisRepository,
        ObjectMapper objectMapper,
        SqlSafetyValidator sqlSafetyValidator
    ) {
        this.contextBuilderService = contextBuilderService;
        this.incidentAnalysisClient = incidentAnalysisClient;
        this.incidentRepository = incidentRepository;
        this.incidentAnalysisRepository = incidentAnalysisRepository;
        this.objectMapper = objectMapper;
        this.sqlSafetyValidator = sqlSafetyValidator;
    }

    @Transactional
    public StoredIncidentAnalysis analyze(String incidentNo) {
        Incident incident = findIncident(incidentNo);
        IncidentAnalysisResult result = incidentAnalysisClient.analyze(contextBuilderService.buildContext(incidentNo));
        List<VerificationSql> checkedVerificationSql = sqlSafetyValidator.validateAll(result.verificationSql());
        IncidentAnalysis analysis = incidentAnalysisRepository.save(new IncidentAnalysis(
            incident,
            result.summary(),
            result.impactScope(),
            writeJson(result.suspectedCauses()),
            writeJson(checkedVerificationSql),
            writeJson(result.additionalChecks()),
            result.mock(),
            Instant.now()
        ));
        incident.markAnalyzed();
        return StoredIncidentAnalysis.from(analysis, objectMapper);
    }

    @Transactional(readOnly = true)
    public StoredIncidentAnalysis getLatestAnalysis(String incidentNo) {
        Incident incident = findIncident(incidentNo);
        IncidentAnalysis analysis = incidentAnalysisRepository.findFirstByIncidentOrderByAnalyzedAtDesc(incident)
            .orElseThrow(() -> new IncidentAnalysisNotFoundException(incidentNo));
        return StoredIncidentAnalysis.from(analysis, objectMapper);
    }

    private Incident findIncident(String incidentNo) {
        return incidentRepository.findByIncidentNo(incidentNo)
            .orElseThrow(() -> new IncidentNotFoundException(incidentNo));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize incident analysis", exception);
        }
    }

    private static <T> T readJson(ObjectMapper objectMapper, String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize incident analysis", exception);
        }
    }

    public record StoredIncidentAnalysis(
        Long id,
        String incidentNo,
        String summary,
        String impactScope,
        List<SuspectedCause> suspectedCauses,
        List<VerificationSql> verificationSql,
        List<String> additionalChecks,
        boolean mock,
        Instant analyzedAt
    ) {

        private static StoredIncidentAnalysis from(IncidentAnalysis analysis, ObjectMapper objectMapper) {
            return new StoredIncidentAnalysis(
                analysis.getId(),
                analysis.getIncident().getIncidentNo(),
                analysis.getSummary(),
                analysis.getImpactScope(),
                readJson(objectMapper, analysis.getSuspectedCausesJson(), SUSPECTED_CAUSE_LIST),
                readJson(objectMapper, analysis.getVerificationSqlJson(), VERIFICATION_SQL_LIST),
                readJson(objectMapper, analysis.getAdditionalChecksJson(), STRING_LIST),
                analysis.isMock(),
                analysis.getAnalyzedAt()
            );
        }
    }
}
