package com.opslens.domain.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OperationalDataRepositoryTest {

    @Autowired
    private SourceSystemRepository sourceSystemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DataIngestionLogRepository dataIngestionLogRepository;

    @Test
    void savesAndLoadsOperationalData() {
        SourceSystem sourceSystem = sourceSystemRepository.save(new SourceSystem("SRC_A", "Source A"));
        Instant now = Instant.parse("2026-09-13T00:00:00Z");

        Customer customer = customerRepository.save(new Customer(
            "CUST-001",
            "Test Customer",
            "010-0000-0000",
            sourceSystem,
            now
        ));

        Order order = orderRepository.save(new Order(
            "ORD-001",
            customer,
            sourceSystem,
            "COMPLETED",
            new BigDecimal("12000.00"),
            now,
            now.plusSeconds(60)
        ));

        paymentRepository.save(new Payment(
            "PAY-001",
            order,
            sourceSystem,
            "PAID",
            new BigDecimal("12000.00"),
            now.plusSeconds(120)
        ));

        dataIngestionLogRepository.save(new DataIngestionLog(
            sourceSystem,
            "orders",
            LocalDate.of(2026, 9, 13),
            100,
            98,
            2,
            "PARTIAL_SUCCESS",
            now.minusSeconds(300),
            now.minusSeconds(120)
        ));

        assertThat(sourceSystemRepository.findByCode("SRC_A")).hasValueSatisfying(saved ->
            assertThat(saved.getName()).isEqualTo("Source A")
        );
        assertThat(customerRepository.findByCustomerNo("CUST-001")).isPresent();
        assertThat(orderRepository.countByOrderedAtBetween(now.minusSeconds(1), now.plusSeconds(1))).isEqualTo(1);
        assertThat(paymentRepository.findByPaymentId("PAY-001")).hasSize(1);
        assertThat(dataIngestionLogRepository.findByTargetTableAndBatchDate("orders", LocalDate.of(2026, 9, 13)))
            .hasSize(1);
    }
}
