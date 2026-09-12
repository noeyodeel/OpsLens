package com.opslens.application.testdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opslens.domain.datasource.Customer;
import com.opslens.domain.datasource.CustomerRepository;
import com.opslens.domain.datasource.DataIngestionLog;
import com.opslens.domain.datasource.DataIngestionLogRepository;
import com.opslens.domain.datasource.Order;
import com.opslens.domain.datasource.OrderRepository;
import com.opslens.domain.datasource.Payment;
import com.opslens.domain.datasource.PaymentRepository;
import com.opslens.domain.datasource.SourceSystem;
import com.opslens.domain.datasource.SourceSystemRepository;

@Service
public class TestDataGenerationService {

    private static final int DEFAULT_DAYS = 8;
    private static final int DEFAULT_SOURCE_COUNT = 3;
    private static final int DEFAULT_CUSTOMERS_PER_SOURCE = 20;
    private static final int DEFAULT_ORDERS_PER_SOURCE_PER_DAY = 40;
    private static final long DEFAULT_SEED = 20260913L;
    private static final LocalDate DEFAULT_BASE_DATE = LocalDate.of(2026, 9, 13);

    private final SourceSystemRepository sourceSystemRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DataIngestionLogRepository dataIngestionLogRepository;

    public TestDataGenerationService(
        SourceSystemRepository sourceSystemRepository,
        CustomerRepository customerRepository,
        OrderRepository orderRepository,
        PaymentRepository paymentRepository,
        DataIngestionLogRepository dataIngestionLogRepository
    ) {
        this.sourceSystemRepository = sourceSystemRepository;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.dataIngestionLogRepository = dataIngestionLogRepository;
    }

    @Transactional
    public TestDataGenerationResult generate(TestDataGenerationCommand command) {
        GenerationOptions options = GenerationOptions.from(command);
        if (options.resetExisting()) {
            clearOperationalData();
        }

        Random random = new Random(options.seed());
        Instant createdAt = options.baseDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        List<SourceSystem> sources = createSourceSystems(options.sourceCount(), createdAt);
        List<Customer> customers = createCustomers(sources, options.customersPerSource(), createdAt);

        int orderCount = 0;
        int paymentCount = 0;
        int ingestionLogCount = 0;

        for (int dayOffset = options.days() - 1; dayOffset >= 0; dayOffset--) {
            LocalDate batchDate = options.baseDate().minusDays(dayOffset);
            for (SourceSystem source : sources) {
                List<Customer> sourceCustomers = customers.stream()
                    .filter(customer -> customer.getSourceSystem().getId().equals(source.getId()))
                    .toList();
                for (int sequence = 1; sequence <= options.ordersPerSourcePerDay(); sequence++) {
                    Instant orderedAt = batchDate.atTime(random.nextInt(24), random.nextInt(60), random.nextInt(60))
                        .toInstant(ZoneOffset.UTC);
                    Customer customer = sourceCustomers.get(random.nextInt(sourceCustomers.size()));
                    BigDecimal amount = randomAmount(random);
                    String orderNo = "ORD-%s-%s-%04d".formatted(source.getCode(), batchDate, sequence);
                    Order order = orderRepository.save(new Order(
                        orderNo,
                        customer,
                        source,
                        "COMPLETED",
                        amount,
                        orderedAt,
                        orderedAt.plusSeconds(30 + random.nextInt(180))
                    ));
                    orderCount++;

                    paymentRepository.save(new Payment(
                        "PAY-%s-%s-%04d".formatted(source.getCode(), batchDate, sequence),
                        order,
                        source,
                        "PAID",
                        amount,
                        orderedAt.plusSeconds(60 + random.nextInt(300))
                    ));
                    paymentCount++;
                }

                Instant batchStartedAt = batchDate.atTime(1, 0).toInstant(ZoneOffset.UTC);
                dataIngestionLogRepository.save(new DataIngestionLog(
                    source,
                    "orders",
                    batchDate,
                    options.ordersPerSourcePerDay(),
                    options.ordersPerSourcePerDay(),
                    0,
                    "SUCCESS",
                    batchStartedAt,
                    batchStartedAt.plusSeconds(600)
                ));
                ingestionLogCount++;
            }
        }

        return new TestDataGenerationResult(
            sources.size(),
            customers.size(),
            orderCount,
            paymentCount,
            ingestionLogCount,
            options.days(),
            options.seed(),
            options.baseDate()
        );
    }

    private void clearOperationalData() {
        paymentRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        dataIngestionLogRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        sourceSystemRepository.deleteAllInBatch();
    }

    private List<SourceSystem> createSourceSystems(int sourceCount, Instant createdAt) {
        List<SourceSystem> sources = new ArrayList<>();
        for (int i = 1; i <= sourceCount; i++) {
            String code = "SRC_%02d".formatted(i);
            sources.add(sourceSystemRepository.save(new SourceSystem(code, "Source System %02d".formatted(i), createdAt)));
        }
        return sources;
    }

    private List<Customer> createCustomers(List<SourceSystem> sources, int customersPerSource, Instant createdAt) {
        List<Customer> customers = new ArrayList<>();
        for (SourceSystem source : sources) {
            for (int i = 1; i <= customersPerSource; i++) {
                customers.add(customerRepository.save(new Customer(
                    "CUST-%s-%04d".formatted(source.getCode(), i),
                    "Customer %s %04d".formatted(source.getCode(), i),
                    "010-%04d-%04d".formatted(source.getId().intValue(), i),
                    source,
                    createdAt.plusSeconds(i)
                )));
            }
        }
        return customers;
    }

    private BigDecimal randomAmount(Random random) {
        int base = 5_000 + random.nextInt(195_000);
        return BigDecimal.valueOf(base).setScale(2, RoundingMode.HALF_UP);
    }

    private record GenerationOptions(
        int days,
        int sourceCount,
        int customersPerSource,
        int ordersPerSourcePerDay,
        long seed,
        LocalDate baseDate,
        boolean resetExisting
    ) {

        private static GenerationOptions from(TestDataGenerationCommand command) {
            TestDataGenerationCommand safeCommand = command == null ? TestDataGenerationCommand.empty() : command;
            return new GenerationOptions(
                valueOrDefault(safeCommand.days(), DEFAULT_DAYS),
                valueOrDefault(safeCommand.sourceCount(), DEFAULT_SOURCE_COUNT),
                valueOrDefault(safeCommand.customersPerSource(), DEFAULT_CUSTOMERS_PER_SOURCE),
                valueOrDefault(safeCommand.ordersPerSourcePerDay(), DEFAULT_ORDERS_PER_SOURCE_PER_DAY),
                safeCommand.seed() == null ? DEFAULT_SEED : safeCommand.seed(),
                safeCommand.baseDate() == null ? DEFAULT_BASE_DATE : safeCommand.baseDate(),
                safeCommand.resetExisting() == null || safeCommand.resetExisting()
            );
        }

        private static int valueOrDefault(Integer value, int defaultValue) {
            return value == null ? defaultValue : value;
        }
    }

    public record TestDataGenerationCommand(
        Integer days,
        Integer sourceCount,
        Integer customersPerSource,
        Integer ordersPerSourcePerDay,
        Long seed,
        LocalDate baseDate,
        Boolean resetExisting
    ) {

        private static TestDataGenerationCommand empty() {
            return new TestDataGenerationCommand(null, null, null, null, null, null, null);
        }
    }

    public record TestDataGenerationResult(
        int sourceSystems,
        int customers,
        int orders,
        int payments,
        int ingestionLogs,
        int days,
        long seed,
        LocalDate baseDate
    ) {
    }
}
