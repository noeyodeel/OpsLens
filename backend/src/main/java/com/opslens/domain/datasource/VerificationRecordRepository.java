package com.opslens.domain.datasource;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationRecordRepository extends JpaRepository<VerificationRecord, Long> {

    List<VerificationRecord> findByVerificationRecordKey(String verificationRecordKey);

    long countByVerifiedAtBetween(Instant from, Instant to);

    List<VerificationRecord> findByTreatmentRecordIn(List<TreatmentRecord> treatmentRecords);

    List<VerificationRecord> findByExternalInstitutionAndVerifiedAtBetween(ExternalInstitution externalInstitution, Instant from, Instant to);

    @Query(value = """
        select coalesce(sum(grouped.record_count - 1), 0)
        from (
            select count(v.id) as record_count
            from verification_records v
            where v.external_institution_id = :externalInstitutionId
              and v.verified_at >= :from
              and v.verified_at < :to
            group by v.verification_record_key
            having count(v.id) > 1
        ) grouped
        """, nativeQuery = true)
    long countDuplicateRowsByExternalInstitutionAndVerifiedAtBetween(
        @Param("externalInstitutionId") Long externalInstitutionId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );
}
