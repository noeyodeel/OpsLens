package com.opslens.application.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.opslens.application.scenario.ScenarioInjectionService.ScenarioInjectionCommand;
import com.opslens.application.scenario.ScenarioInjectionService.ScenarioInjectionResult;
import com.opslens.application.testdata.TestDataGenerationService;
import com.opslens.application.testdata.TestDataGenerationService.TestDataGenerationCommand;
import com.opslens.domain.datasource.CustomerRepository;
import com.opslens.domain.datasource.OrderRepository;
import com.opslens.domain.datasource.PaymentRepository;
import com.opslens.domain.datasource.SourceSystem;
import com.opslens.domain.datasource.SourceSystemRepository;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.scenario.ScenarioRepository;
import com.opslens.domain.scenario.ScenarioType;

@DataJpaTest(showSql = false)
@Import({TestDataGenerationService.class, ScenarioInjectionService.class})
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ScenarioInjectionServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 13);

    @Autowired
    private TestDataGenerationService testDataGenerationService;

    @Autowired
    private ScenarioInjectionService scenarioInjectionService;

    @Autowired
    private SourceSystemRepository sourceSystemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ScenarioRepository scenarioRepository;

    private SourceSystem source;

    @BeforeEach
    void setUp() {
        testDataGenerationService.generate(new TestDataGenerationCommand(
            2,
            2,
            5,
            10,
            17L,
            BASE_DATE,
            true
        ));
        source = sourceSystemRepository.findByCode("SRC_02").orElseThrow();
    }

    @Test
    void injectsOrderVolumeDropScenario() {
        assertThat(ordersForTargetDate()).hasSize(10);

        ScenarioInjectionResult result = scenarioInjectionService.inject(new ScenarioInjectionCommand(
            ScenarioType.ORDER_VOLUME_DROP,
            "SRC_02",
            BASE_DATE
        ));

        assertThat(result.scenarioType()).isEqualTo(ScenarioType.ORDER_VOLUME_DROP);
        assertThat(result.expectedIncidentType()).isEqualTo(AnomalyType.COUNT_DROP);
        assertThat(result.affectedRows()).isEqualTo(8);
        assertThat(ordersForTargetDate()).hasSize(2);
        assertThat(paymentsForTargetDate()).hasSize(2);
        assertThat(scenarioRepository.count()).isEqualTo(1);
    }

    @Test
    void injectsCustomerPhoneNullSpikeScenario() {
        assertThat(customerRepository.findBySourceSystem(source))
            .allMatch(customer -> customer.getCustomerPhone() != null);

        ScenarioInjectionResult result = scenarioInjectionService.inject(new ScenarioInjectionCommand(
            ScenarioType.CUSTOMER_PHONE_NULL_SPIKE,
            "SRC_02",
            BASE_DATE
        ));

        long nullPhones = customerRepository.findBySourceSystem(source).stream()
            .filter(customer -> customer.getCustomerPhone() == null)
            .count();

        assertThat(result.expectedIncidentType()).isEqualTo(AnomalyType.NULL_SPIKE);
        assertThat(result.affectedRows()).isEqualTo(4);
        assertThat(nullPhones).isEqualTo(4);
    }

    @Test
    void injectsDuplicatePaymentIdScenario() {
        ScenarioInjectionResult result = scenarioInjectionService.inject(new ScenarioInjectionCommand(
            ScenarioType.DUPLICATE_PAYMENT_ID,
            "SRC_02",
            BASE_DATE
        ));

        String duplicatePaymentId = "PAY-DUPLICATE-SRC_02-2026-09-13";

        assertThat(result.expectedIncidentType()).isEqualTo(AnomalyType.DUPLICATE_DETECTED);
        assertThat(result.affectedRows()).isEqualTo(2);
        assertThat(paymentRepository.findByPaymentId(duplicatePaymentId)).hasSize(2);
    }

    private java.util.List<com.opslens.domain.datasource.Order> ordersForTargetDate() {
        return orderRepository.findBySourceSystemAndOrderedAtBetween(
            source,
            BASE_DATE.atStartOfDay().toInstant(ZoneOffset.UTC),
            BASE_DATE.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        );
    }

    private java.util.List<com.opslens.domain.datasource.Payment> paymentsForTargetDate() {
        return paymentRepository.findBySourceSystemAndPaidAtBetween(
            source,
            BASE_DATE.atStartOfDay().toInstant(ZoneOffset.UTC),
            BASE_DATE.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        );
    }
}
