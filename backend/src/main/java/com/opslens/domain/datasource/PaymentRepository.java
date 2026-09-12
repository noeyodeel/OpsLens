package com.opslens.domain.datasource;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByPaymentId(String paymentId);

    long countByPaidAtBetween(Instant from, Instant to);
}
