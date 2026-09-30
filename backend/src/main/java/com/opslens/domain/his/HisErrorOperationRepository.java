package com.opslens.domain.his;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HisErrorOperationRepository extends JpaRepository<HisErrorOperationEntity, Long> {

    Optional<HisErrorOperationEntity> findByErrorId(Long errorId);
}
