package com.opslens.domain.datasource;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordSubjectRepository extends JpaRepository<RecordSubject, Long> {

    Optional<RecordSubject> findBySubjectNo(String subjectNo);

    List<RecordSubject> findByExternalInstitution(ExternalInstitution externalInstitution);
}
