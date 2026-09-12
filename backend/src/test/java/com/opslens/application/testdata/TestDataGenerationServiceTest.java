package com.opslens.application.testdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.opslens.application.testdata.TestDataGenerationService.TestDataGenerationCommand;
import com.opslens.application.testdata.TestDataGenerationService.TestDataGenerationResult;
import com.opslens.domain.datasource.CustomerRepository;
import com.opslens.domain.datasource.DataIngestionLogRepository;
import com.opslens.domain.datasource.OrderRepository;
import com.opslens.domain.datasource.PaymentRepository;
import com.opslens.domain.datasource.SourceSystemRepository;

@DataJpaTest(showSql = false)
@Import(TestDataGenerationService.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TestDataGenerationServiceTest {

    @Autowired
    private TestDataGenerationService testDataGenerationService;

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
    void generatesNormalOperationalDataFromDefaults() {
        TestDataGenerationResult result = testDataGenerationService.generate(null);

        assertThat(result.sourceSystems()).isEqualTo(3);
        assertThat(result.customers()).isEqualTo(60);
        assertThat(result.orders()).isEqualTo(960);
        assertThat(result.payments()).isEqualTo(960);
        assertThat(result.ingestionLogs()).isEqualTo(24);
        assertThat(result.days()).isEqualTo(8);
        assertThat(result.seed()).isEqualTo(20260913L);
        assertThat(result.baseDate()).isEqualTo(LocalDate.of(2026, 9, 13));

        assertThat(sourceSystemRepository.count()).isEqualTo(3);
        assertThat(customerRepository.count()).isEqualTo(60);
        assertThat(orderRepository.count()).isEqualTo(960);
        assertThat(paymentRepository.count()).isEqualTo(960);
        assertThat(dataIngestionLogRepository.count()).isEqualTo(24);
    }

    @Test
    void resetsExistingDataBeforeGeneratingAgain() {
        TestDataGenerationCommand command = new TestDataGenerationCommand(
            2,
            2,
            3,
            4,
            7L,
            LocalDate.of(2026, 9, 13),
            true
        );

        testDataGenerationService.generate(command);
        TestDataGenerationResult result = testDataGenerationService.generate(command);

        assertThat(result.sourceSystems()).isEqualTo(2);
        assertThat(result.customers()).isEqualTo(6);
        assertThat(result.orders()).isEqualTo(16);
        assertThat(result.payments()).isEqualTo(16);
        assertThat(result.ingestionLogs()).isEqualTo(4);

        assertThat(sourceSystemRepository.count()).isEqualTo(2);
        assertThat(customerRepository.count()).isEqualTo(6);
        assertThat(orderRepository.count()).isEqualTo(16);
        assertThat(paymentRepository.count()).isEqualTo(16);
        assertThat(dataIngestionLogRepository.count()).isEqualTo(4);
    }
}
