package com.opslens.domain.datasource;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceSystemRepository extends JpaRepository<SourceSystem, Long> {

    Optional<SourceSystem> findByCode(String code);
}
