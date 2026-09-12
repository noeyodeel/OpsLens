package com.opslens.application.scenario;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opslens.domain.datasource.Customer;
import com.opslens.domain.datasource.CustomerRepository;
import com.opslens.domain.datasource.Order;
import com.opslens.domain.datasource.OrderRepository;
import com.opslens.domain.datasource.Payment;
import com.opslens.domain.datasource.PaymentRepository;
import com.opslens.domain.datasource.SourceSystem;
import com.opslens.domain.datasource.SourceSystemRepository;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.scenario.Scenario;
import com.opslens.domain.scenario.ScenarioRepository;
import com.opslens.domain.scenario.ScenarioType;

@Service
public class ScenarioInjectionService {

    private static final String DEFAULT_SOURCE_CODE = "SRC_02";
    private static final LocalDate DEFAULT_TARGET_DATE = LocalDate.of(2026, 9, 13);

    private final SourceSystemRepository sourceSystemRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ScenarioRepository scenarioRepository;
    private final Clock clock;

    public ScenarioInjectionService(
        SourceSystemRepository sourceSystemRepository,
        CustomerRepository customerRepository,
        OrderRepository orderRepository,
        PaymentRepository paymentRepository,
        ScenarioRepository scenarioRepository
    ) {
        this.sourceSystemRepository = sourceSystemRepository;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.scenarioRepository = scenarioRepository;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public List<ScenarioDefinition> definitions() {
        return List.of(
            new ScenarioDefinition(
                ScenarioType.ORDER_VOLUME_DROP,
                "Order volume drop",
                "Deletes 80% of orders and related payments for a source/date.",
                AnomalyType.COUNT_DROP
            ),
            new ScenarioDefinition(
                ScenarioType.CUSTOMER_PHONE_NULL_SPIKE,
                "Customer phone NULL spike",
                "Clears phone numbers for 80% of customers in a source system.",
                AnomalyType.NULL_SPIKE
            ),
            new ScenarioDefinition(
                ScenarioType.DUPLICATE_PAYMENT_ID,
                "Duplicate payment ID",
                "Rewrites multiple payment rows to share the same business payment ID.",
                AnomalyType.DUPLICATE_DETECTED
            )
        );
    }

    @Transactional
    public ScenarioInjectionResult inject(ScenarioInjectionCommand command) {
        ScenarioInjectionCommand safeCommand = command == null ? ScenarioInjectionCommand.empty() : command;
        ScenarioType scenarioType = safeCommand.scenarioType() == null
            ? ScenarioType.ORDER_VOLUME_DROP
            : safeCommand.scenarioType();
        String sourceCode = safeCommand.targetSourceCode() == null ? DEFAULT_SOURCE_CODE : safeCommand.targetSourceCode();
        LocalDate targetDate = safeCommand.targetDate() == null ? DEFAULT_TARGET_DATE : safeCommand.targetDate();
        SourceSystem sourceSystem = sourceSystemRepository.findByCode(sourceCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown source system: " + sourceCode));

        Scenario scenario = switch (scenarioType) {
            case ORDER_VOLUME_DROP -> injectOrderVolumeDrop(sourceSystem, targetDate);
            case CUSTOMER_PHONE_NULL_SPIKE -> injectCustomerPhoneNullSpike(sourceSystem, targetDate);
            case DUPLICATE_PAYMENT_ID -> injectDuplicatePaymentId(sourceSystem, targetDate);
        };

        Scenario saved = scenarioRepository.save(scenario);
        return new ScenarioInjectionResult(
            saved.getId(),
            saved.getScenarioType(),
            saved.getName(),
            saved.getTargetSourceCode(),
            saved.getTargetDate(),
            saved.getAffectedRows(),
            saved.getExpectedIncidentType(),
            saved.getExpectedCause(),
            saved.getInjectedAt()
        );
    }

    private Scenario injectOrderVolumeDrop(SourceSystem sourceSystem, LocalDate targetDate) {
        List<Order> targetOrders = findOrders(sourceSystem, targetDate).stream()
            .sorted(Comparator.comparing(Order::getOrderNo))
            .toList();
        int deleteCount = Math.max(1, (int) Math.floor(targetOrders.size() * 0.8));
        List<Order> ordersToDelete = targetOrders.stream().limit(deleteCount).toList();
        List<Payment> paymentsToDelete = paymentRepository.findByOrderIn(ordersToDelete);

        paymentRepository.deleteAllInBatch(paymentsToDelete);
        orderRepository.deleteAllInBatch(ordersToDelete);

        return new Scenario(
            "Order volume drop for " + sourceSystem.getCode(),
            ScenarioType.ORDER_VOLUME_DROP,
            sourceSystem.getCode(),
            targetDate,
            ordersToDelete.size(),
            AnomalyType.COUNT_DROP,
            "Orders from %s dropped because most records for %s were not ingested.".formatted(sourceSystem.getCode(), targetDate),
            Instant.now(clock)
        );
    }

    private Scenario injectCustomerPhoneNullSpike(SourceSystem sourceSystem, LocalDate targetDate) {
        List<Customer> customers = customerRepository.findBySourceSystem(sourceSystem).stream()
            .sorted(Comparator.comparing(Customer::getCustomerNo))
            .toList();
        int affectedRows = Math.max(1, (int) Math.floor(customers.size() * 0.8));
        customers.stream()
            .limit(affectedRows)
            .forEach(Customer::clearPhone);

        return new Scenario(
            "Customer phone NULL spike for " + sourceSystem.getCode(),
            ScenarioType.CUSTOMER_PHONE_NULL_SPIKE,
            sourceSystem.getCode(),
            targetDate,
            affectedRows,
            AnomalyType.NULL_SPIKE,
            "Customer phone values from %s became NULL during source data ingestion.".formatted(sourceSystem.getCode()),
            Instant.now(clock)
        );
    }

    private Scenario injectDuplicatePaymentId(SourceSystem sourceSystem, LocalDate targetDate) {
        List<Payment> payments = paymentRepository.findBySourceSystemAndPaidAtBetween(
                sourceSystem,
                startOfDay(targetDate),
                startOfDay(targetDate.plusDays(1))
            ).stream()
            .sorted(Comparator.comparing(Payment::getPaymentId))
            .toList();
        int affectedRows = Math.min(Math.max(2, payments.size() / 4), payments.size());
        String duplicatePaymentId = "PAY-DUPLICATE-%s-%s".formatted(sourceSystem.getCode(), targetDate);
        payments.stream()
            .limit(affectedRows)
            .forEach(payment -> payment.replacePaymentId(duplicatePaymentId));

        return new Scenario(
            "Duplicate payment ID for " + sourceSystem.getCode(),
            ScenarioType.DUPLICATE_PAYMENT_ID,
            sourceSystem.getCode(),
            targetDate,
            affectedRows,
            AnomalyType.DUPLICATE_DETECTED,
            "Multiple payment rows from %s share the same payment_id.".formatted(sourceSystem.getCode()),
            Instant.now(clock)
        );
    }

    private List<Order> findOrders(SourceSystem sourceSystem, LocalDate targetDate) {
        return orderRepository.findBySourceSystemAndOrderedAtBetween(
            sourceSystem,
            startOfDay(targetDate),
            startOfDay(targetDate.plusDays(1))
        );
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public record ScenarioDefinition(
        ScenarioType scenarioType,
        String name,
        String description,
        AnomalyType expectedIncidentType
    ) {
    }

    public record ScenarioInjectionCommand(
        ScenarioType scenarioType,
        String targetSourceCode,
        LocalDate targetDate
    ) {

        private static ScenarioInjectionCommand empty() {
            return new ScenarioInjectionCommand(null, null, null);
        }
    }

    public record ScenarioInjectionResult(
        Long scenarioId,
        ScenarioType scenarioType,
        String name,
        String targetSourceCode,
        LocalDate targetDate,
        int affectedRows,
        AnomalyType expectedIncidentType,
        String expectedCause,
        Instant injectedAt
    ) {
    }
}
