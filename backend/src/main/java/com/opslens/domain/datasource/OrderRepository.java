package com.opslens.domain.datasource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    long countByOrderedAtBetween(Instant from, Instant to);

    long countBySourceSystemAndOrderedAtBetween(SourceSystem sourceSystem, Instant from, Instant to);

    List<Order> findBySourceSystemAndOrderedAtBetween(SourceSystem sourceSystem, Instant from, Instant to);
}
