package com.opslens.application.testdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

@Service
public class TestDataGenerationService {

    private static final int DEFAULT_DAYS = 8;
    private static final int DEFAULT_INSTITUTION_COUNT = 3;
    private static final int DEFAULT_SUBJECTS_PER_INSTITUTION = 20;
    private static final int DEFAULT_RECORDS_PER_INSTITUTION_PER_DAY = 40;
    private static final long DEFAULT_SEED = 20260913L;
    private static final LocalDate DEFAULT_BASE_DATE = LocalDate.of(2026, 9, 13);

    private final ExternalInstitutionRepository externalInstitutionRepository;
    private final RecordSubjectRepository recordSubjectRepository;
    private final TreatmentRecordRepository treatmentRecordRepository;
    private final VerificationRecordRepository verificationRecordRepository;
    private final DataIngestionLogRepository dataIngestionLogRepository;

    public TestDataGenerationService(
        ExternalInstitutionRepository externalInstitutionRepository,
        RecordSubjectRepository recordSubjectRepository,
        TreatmentRecordRepository treatmentRecordRepository,
        VerificationRecordRepository verificationRecordRepository,
        DataIngestionLogRepository dataIngestionLogRepository
    ) {
        this.externalInstitutionRepository = externalInstitutionRepository;
        this.recordSubjectRepository = recordSubjectRepository;
        this.treatmentRecordRepository = treatmentRecordRepository;
        this.verificationRecordRepository = verificationRecordRepository;
        this.dataIngestionLogRepository = dataIngestionLogRepository;
    }

    @Transactional
    public TestDataGenerationResult generate(TestDataGenerationCommand command) {
        GenerationOptions options = GenerationOptions.from(command);
        if (options.resetExisting()) {
            clearOperationalData();
        }

        Random random = new Random(options.seed());
        Instant createdAt = options.baseDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        List<ExternalInstitution> institutions = createExternalInstitutions(options.institutionCount(), createdAt);
        List<RecordSubject> recordSubjects = createRecordSubjects(institutions, options.subjectsPerInstitution(), createdAt);

        int treatmentRecordCount = 0;
        int verificationRecordCount = 0;
        int ingestionLogCount = 0;

        for (int dayOffset = options.days() - 1; dayOffset >= 0; dayOffset--) {
            LocalDate batchDate = options.baseDate().minusDays(dayOffset);
            for (ExternalInstitution institution : institutions) {
                List<RecordSubject> institutionRecordSubjects = recordSubjects.stream()
                    .filter(recordSubject -> recordSubject.getExternalInstitution().getId().equals(institution.getId()))
                    .toList();
                for (int sequence = 1; sequence <= options.recordsPerInstitutionPerDay(); sequence++) {
                    Instant recordedAt = batchDate.atTime(random.nextInt(24), random.nextInt(60), random.nextInt(60))
                        .toInstant(ZoneOffset.UTC);
                    RecordSubject recordSubject = institutionRecordSubjects.get(random.nextInt(institutionRecordSubjects.size()));
                    BigDecimal recordValue = randomAmount(random);
                    String treatmentRecordNo = "TR-%s-%s-%04d".formatted(institution.getCode(), batchDate, sequence);
                    TreatmentRecord treatmentRecord = treatmentRecordRepository.save(new TreatmentRecord(
                        treatmentRecordNo,
                        recordSubject,
                        institution,
                        "COMPLETED",
                        recordValue,
                        recordedAt,
                        recordedAt.plusSeconds(30 + random.nextInt(180))
                    ));
                    treatmentRecordCount++;

                    verificationRecordRepository.save(new VerificationRecord(
                        "VR-%s-%s-%04d".formatted(institution.getCode(), batchDate, sequence),
                        treatmentRecord,
                        institution,
                        "VERIFIED",
                        recordValue,
                        recordedAt.plusSeconds(60 + random.nextInt(300))
                    ));
                    verificationRecordCount++;
                }

                Instant batchStartedAt = batchDate.atTime(1, 0).toInstant(ZoneOffset.UTC);
                dataIngestionLogRepository.save(new DataIngestionLog(
                    institution,
                    "treatment_records",
                    batchDate,
                    options.recordsPerInstitutionPerDay(),
                    options.recordsPerInstitutionPerDay(),
                    0,
                    "SUCCESS",
                    batchStartedAt,
                    batchStartedAt.plusSeconds(600)
                ));
                ingestionLogCount++;
            }
        }

        return new TestDataGenerationResult(
            institutions.size(),
            recordSubjects.size(),
            treatmentRecordCount,
            verificationRecordCount,
            ingestionLogCount,
            options.days(),
            options.seed(),
            options.baseDate()
        );
    }

    private void clearOperationalData() {
        verificationRecordRepository.deleteAllInBatch();
        treatmentRecordRepository.deleteAllInBatch();
        dataIngestionLogRepository.deleteAllInBatch();
        recordSubjectRepository.deleteAllInBatch();
        externalInstitutionRepository.deleteAllInBatch();
    }

    private List<ExternalInstitution> createExternalInstitutions(int institutionCount, Instant createdAt) {
        List<ExternalInstitution> institutions = new ArrayList<>();
        for (int i = 1; i <= institutionCount; i++) {
            String code = "INST_%02d".formatted(i);
            institutions.add(externalInstitutionRepository.save(new ExternalInstitution(code, "Institution %02d".formatted(i), createdAt)));
        }
        return institutions;
    }

    private List<RecordSubject> createRecordSubjects(List<ExternalInstitution> institutions, int subjectsPerInstitution, Instant createdAt) {
        List<RecordSubject> recordSubjects = new ArrayList<>();
        for (ExternalInstitution institution : institutions) {
            for (int i = 1; i <= subjectsPerInstitution; i++) {
                recordSubjects.add(recordSubjectRepository.save(new RecordSubject(
                    "SUBJ-%s-%04d".formatted(institution.getCode(), i),
                    "Subject %s %04d".formatted(institution.getCode(), i),
                    "REQ-%04d-%04d".formatted(institution.getId().intValue(), i),
                    institution,
                    createdAt.plusSeconds(i)
                )));
            }
        }
        return recordSubjects;
    }

    private BigDecimal randomAmount(Random random) {
        int base = 5_000 + random.nextInt(195_000);
        return BigDecimal.valueOf(base).setScale(2, RoundingMode.HALF_UP);
    }

    private record GenerationOptions(
        int days,
        int institutionCount,
        int subjectsPerInstitution,
        int recordsPerInstitutionPerDay,
        long seed,
        LocalDate baseDate,
        boolean resetExisting
    ) {

        private static GenerationOptions from(TestDataGenerationCommand command) {
            TestDataGenerationCommand safeCommand = command == null ? TestDataGenerationCommand.empty() : command;
            return new GenerationOptions(
                valueOrDefault(safeCommand.days(), DEFAULT_DAYS),
                valueOrDefault(safeCommand.institutionCount(), DEFAULT_INSTITUTION_COUNT),
                valueOrDefault(safeCommand.subjectsPerInstitution(), DEFAULT_SUBJECTS_PER_INSTITUTION),
                valueOrDefault(safeCommand.recordsPerInstitutionPerDay(), DEFAULT_RECORDS_PER_INSTITUTION_PER_DAY),
                safeCommand.seed() == null ? DEFAULT_SEED : safeCommand.seed(),
                safeCommand.baseDate() == null ? DEFAULT_BASE_DATE : safeCommand.baseDate(),
                safeCommand.resetExisting() == null || safeCommand.resetExisting()
            );
        }

        private static int valueOrDefault(Integer value, int defaultValue) {
            return value == null ? defaultValue : value;
        }
    }

    public record TestDataGenerationCommand(
        Integer days,
        Integer institutionCount,
        Integer subjectsPerInstitution,
        Integer recordsPerInstitutionPerDay,
        Long seed,
        LocalDate baseDate,
        Boolean resetExisting
    ) {

        private static TestDataGenerationCommand empty() {
            return new TestDataGenerationCommand(null, null, null, null, null, null, null);
        }
    }

    public record TestDataGenerationResult(
        int externalInstitutions,
        int recordSubjects,
        int treatmentRecords,
        int verificationRecords,
        int ingestionLogs,
        int days,
        long seed,
        LocalDate baseDate
    ) {
    }
}
