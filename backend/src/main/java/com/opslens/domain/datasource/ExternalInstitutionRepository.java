package com.opslens.domain.datasource;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalInstitutionRepository extends JpaRepository<ExternalInstitution, Long> {

    Optional<ExternalInstitution> findByCode(String code);
}
