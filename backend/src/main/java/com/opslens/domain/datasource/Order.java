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
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_system_id", nullable = false)
    private SourceSystem sourceSystem;

    @Column(nullable = false, length = 30)
    private String orderStatus;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal orderAmount;

    @Column(nullable = false)
    private Instant orderedAt;

    @Column(nullable = false)
    private Instant ingestedAt;

    protected Order() {
    }

    public Order(
        String orderNo,
        Customer customer,
        SourceSystem sourceSystem,
        String orderStatus,
        BigDecimal orderAmount,
        Instant orderedAt,
        Instant ingestedAt
    ) {
        this.orderNo = orderNo;
        this.customer = customer;
        this.sourceSystem = sourceSystem;
        this.orderStatus = orderStatus;
        this.orderAmount = orderAmount;
        this.orderedAt = orderedAt;
        this.ingestedAt = ingestedAt;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Customer getCustomer() {
        return customer;
    }

    public SourceSystem getSourceSystem() {
        return sourceSystem;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public BigDecimal getOrderAmount() {
        return orderAmount;
    }

    public Instant getOrderedAt() {
        return orderedAt;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
