package com.opslens.application.scenario;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opslens.domain.datasource.RecordSubject;
import com.opslens.domain.datasource.RecordSubjectRepository;
import com.opslens.domain.datasource.DataIngestionLog;
import com.opslens.domain.datasource.DataIngestionLogRepository;
import com.opslens.domain.datasource.TreatmentRecord;
import com.opslens.domain.datasource.TreatmentRecordRepository;
import com.opslens.domain.datasource.VerificationRecord;
import com.opslens.domain.datasource.VerificationRecordRepository;
import com.opslens.domain.datasource.ExternalInstitution;
import com.opslens.domain.datasource.ExternalInstitutionRepository;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.scenario.Scenario;
import com.opslens.domain.scenario.ScenarioRepository;
import com.opslens.domain.scenario.ScenarioType;

@Service
public class ScenarioInjectionService {

    private static final String DEFAULT_INSTITUTION_CODE = "INST_02";
    private static final LocalDate DEFAULT_TARGET_DATE = LocalDate.of(2026, 9, 13);

    private final ExternalInstitutionRepository externalInstitutionRepository;
    private final RecordSubjectRepository recordSubjectRepository;
    private final TreatmentRecordRepository treatmentRecordRepository;
    private final VerificationRecordRepository verificationRecordRepository;
    private final DataIngestionLogRepository dataIngestionLogRepository;
    private final ScenarioRepository scenarioRepository;
    private final Clock clock;

    public ScenarioInjectionService(
        ExternalInstitutionRepository externalInstitutionRepository,
        RecordSubjectRepository recordSubjectRepository,
        TreatmentRecordRepository treatmentRecordRepository,
        VerificationRecordRepository verificationRecordRepository,
        DataIngestionLogRepository dataIngestionLogRepository,
        ScenarioRepository scenarioRepository
    ) {
        this.externalInstitutionRepository = externalInstitutionRepository;
        this.recordSubjectRepository = recordSubjectRepository;
        this.treatmentRecordRepository = treatmentRecordRepository;
        this.verificationRecordRepository = verificationRecordRepository;
        this.dataIngestionLogRepository = dataIngestionLogRepository;
        this.scenarioRepository = scenarioRepository;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public List<ScenarioDefinition> definitions() {
        return List.of(
            new ScenarioDefinition(
                ScenarioType.TREATMENT_RECORD_VOLUME_DROP,
                "Treatment record volume drop",
                "Deletes 80% of treatment records and related verification records for an institution/date.",
                AnomalyType.COUNT_DROP
            ),
            new ScenarioDefinition(
                ScenarioType.REQUIRED_FIELD_NULL_SPIKE,
                "Required field NULL spike",
                "Clears a required field for 80% of record subjects in an external institution.",
                AnomalyType.NULL_SPIKE
            ),
            new ScenarioDefinition(
                ScenarioType.DUPLICATE_RECORD_KEY,
                "Duplicate record key",
                "Rewrites multiple verification records to share the same business record key.",
                AnomalyType.DUPLICATE_DETECTED
            ),
            new ScenarioDefinition(
                ScenarioType.INSTITUTION_DATA_MISSING,
                "Institution data missing",
                "Removes all treatment and verification records for an institution/date to simulate a missing institution transmission.",
                AnomalyType.SOURCE_MISSING
            )
        );
    }

    @Transactional
    public ScenarioInjectionResult inject(ScenarioInjectionCommand command) {
        ScenarioInjectionCommand safeCommand = command == null ? ScenarioInjectionCommand.empty() : command;
        ScenarioType scenarioType = safeCommand.scenarioType() == null
            ? ScenarioType.TREATMENT_RECORD_VOLUME_DROP
            : safeCommand.scenarioType();
        String institutionCode = safeCommand.targetInstitutionCode() == null ? DEFAULT_INSTITUTION_CODE : safeCommand.targetInstitutionCode();
        LocalDate targetDate = safeCommand.targetDate() == null ? DEFAULT_TARGET_DATE : safeCommand.targetDate();
        ExternalInstitution externalInstitution = externalInstitutionRepository.findByCode(institutionCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown external institution: " + institutionCode));

        Scenario scenario = switch (scenarioType) {
            case TREATMENT_RECORD_VOLUME_DROP -> injectTreatmentRecordVolumeDrop(externalInstitution, targetDate);
            case REQUIRED_FIELD_NULL_SPIKE -> injectRequiredFieldNullSpike(externalInstitution, targetDate);
            case DUPLICATE_RECORD_KEY -> injectDuplicateRecordKey(externalInstitution, targetDate);
            case INSTITUTION_DATA_MISSING -> injectInstitutionDataMissing(externalInstitution, targetDate);
        };

        Scenario saved = scenarioRepository.save(scenario);
        return new ScenarioInjectionResult(
            saved.getId(),
            saved.getScenarioType(),
            saved.getName(),
            saved.getTargetInstitutionCode(),
            saved.getTargetDate(),
            saved.getAffectedRows(),
            saved.getExpectedIncidentType(),
            saved.getExpectedCause(),
            saved.getInjectedAt()
        );
    }

    private Scenario injectTreatmentRecordVolumeDrop(ExternalInstitution externalInstitution, LocalDate targetDate) {
        List<TreatmentRecord> targetTreatmentRecords = findTreatmentRecords(externalInstitution, targetDate).stream()
            .sorted(Comparator.comparing(TreatmentRecord::getTreatmentRecordNo))
            .toList();
        int deleteCount = Math.max(1, (int) Math.floor(targetTreatmentRecords.size() * 0.8));
        List<TreatmentRecord> treatmentRecordsToDelete = targetTreatmentRecords.stream().limit(deleteCount).toList();
        List<VerificationRecord> verificationRecordsToDelete = verificationRecordRepository.findByTreatmentRecordIn(treatmentRecordsToDelete);

        verificationRecordRepository.deleteAllInBatch(verificationRecordsToDelete);
        treatmentRecordRepository.deleteAllInBatch(treatmentRecordsToDelete);

        return new Scenario(
            "Treatment record volume drop for " + externalInstitution.getCode(),
            ScenarioType.TREATMENT_RECORD_VOLUME_DROP,
            externalInstitution.getCode(),
            targetDate,
            treatmentRecordsToDelete.size(),
            AnomalyType.COUNT_DROP,
            "Treatment records from %s dropped because most records for %s were not ingested.".formatted(externalInstitution.getCode(), targetDate),
            Instant.now(clock)
        );
    }

    private Scenario injectRequiredFieldNullSpike(ExternalInstitution externalInstitution, LocalDate targetDate) {
        List<RecordSubject> recordSubjects = recordSubjectRepository.findByExternalInstitution(externalInstitution).stream()
            .sorted(Comparator.comparing(RecordSubject::getRecordSubjectNo))
            .toList();
        int affectedRows = Math.max(1, (int) Math.floor(recordSubjects.size() * 0.8));
        recordSubjects.stream()
            .limit(affectedRows)
            .forEach(RecordSubject::clearRequiredField);

        return new Scenario(
            "Required field NULL spike for " + externalInstitution.getCode(),
            ScenarioType.REQUIRED_FIELD_NULL_SPIKE,
            externalInstitution.getCode(),
            targetDate,
            affectedRows,
            AnomalyType.NULL_SPIKE,
            "Required field values from %s became NULL during institution data ingestion.".formatted(externalInstitution.getCode()),
            Instant.now(clock)
        );
    }

    private Scenario injectDuplicateRecordKey(ExternalInstitution externalInstitution, LocalDate targetDate) {
        List<VerificationRecord> verificationRecords = verificationRecordRepository.findByExternalInstitutionAndVerifiedAtBetween(
                externalInstitution,
                startOfDay(targetDate),
                startOfDay(targetDate.plusDays(1))
            ).stream()
            .sorted(Comparator.comparing(VerificationRecord::getVerificationRecordKey))
            .toList();
        int affectedRows = Math.min(Math.max(2, verificationRecords.size() / 4), verificationRecords.size());
        String duplicateVerificationRecordKey = "VR-DUPLICATE-%s-%s".formatted(externalInstitution.getCode(), targetDate);
        verificationRecords.stream()
            .limit(affectedRows)
            .forEach(verificationRecord -> verificationRecord.replaceVerificationRecordKey(duplicateVerificationRecordKey));

        return new Scenario(
            "Duplicate record key for " + externalInstitution.getCode(),
            ScenarioType.DUPLICATE_RECORD_KEY,
            externalInstitution.getCode(),
            targetDate,
            affectedRows,
            AnomalyType.DUPLICATE_DETECTED,
            "Multiple verification records from %s share the same verification_record_key.".formatted(externalInstitution.getCode()),
            Instant.now(clock)
        );
    }

    private Scenario injectInstitutionDataMissing(ExternalInstitution externalInstitution, LocalDate targetDate) {
        List<TreatmentRecord> treatmentRecordsToDelete = findTreatmentRecords(externalInstitution, targetDate);
        List<VerificationRecord> verificationRecordsToDelete = verificationRecordRepository.findByTreatmentRecordIn(treatmentRecordsToDelete);
        List<DataIngestionLog> ingestionLogsToDelete = dataIngestionLogRepository.findByExternalInstitutionAndTargetTableAndBatchDate(
            externalInstitution,
            "treatment_records",
            targetDate
        );

        verificationRecordRepository.deleteAllInBatch(verificationRecordsToDelete);
        treatmentRecordRepository.deleteAllInBatch(treatmentRecordsToDelete);
        dataIngestionLogRepository.deleteAllInBatch(ingestionLogsToDelete);

        return new Scenario(
            "Institution data missing for " + externalInstitution.getCode(),
            ScenarioType.INSTITUTION_DATA_MISSING,
            externalInstitution.getCode(),
            targetDate,
            treatmentRecordsToDelete.size(),
            AnomalyType.SOURCE_MISSING,
            "No treatment records were received from %s for %s.".formatted(externalInstitution.getCode(), targetDate),
            Instant.now(clock)
        );
    }

    private List<TreatmentRecord> findTreatmentRecords(ExternalInstitution externalInstitution, LocalDate targetDate) {
        return treatmentRecordRepository.findByExternalInstitutionAndRecordedAtBetween(
            externalInstitution,
            startOfDay(targetDate),
            startOfDay(targetDate.plusDays(1))
        );
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public record ScenarioDefinition(
        ScenarioType scenarioType,
        String name,
        String description,
        AnomalyType expectedIncidentType
    ) {
    }

    public record ScenarioInjectionCommand(
        ScenarioType scenarioType,
        String targetInstitutionCode,
        LocalDate targetDate
    ) {

        private static ScenarioInjectionCommand empty() {
            return new ScenarioInjectionCommand(null, null, null);
        }
    }

    public record ScenarioInjectionResult(
        Long scenarioId,
        ScenarioType scenarioType,
        String name,
        String targetInstitutionCode,
        LocalDate targetDate,
        int affectedRows,
        AnomalyType expectedIncidentType,
        String expectedCause,
        Instant injectedAt
    ) {
    }
}
