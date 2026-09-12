package com.opslens.domain.datasource;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_system_id", nullable = false)
    private SourceSystem sourceSystem;

    @Column(nullable = false, length = 30)
    private String paymentStatus;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant paidAt;

    protected Payment() {
    }

    public Payment(
        String paymentId,
        Order order,
        SourceSystem sourceSystem,
        String paymentStatus,
        BigDecimal amount,
        Instant paidAt
    ) {
        this.paymentId = paymentId;
        this.order = order;
        this.sourceSystem = sourceSystem;
        this.paymentStatus = paymentStatus;
        this.amount = amount;
        this.paidAt = paidAt;
    }

    public Long getId() {
        return id;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public Order getOrder() {
        return order;
    }

    public SourceSystem getSourceSystem() {
        return sourceSystem;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
