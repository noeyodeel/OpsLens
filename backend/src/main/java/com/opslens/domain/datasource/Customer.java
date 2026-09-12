package com.opslens.domain.datasource;

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
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String customerNo;

    @Column(nullable = false, length = 100)
    private String customerName;

    @Column(length = 30)
    private String customerPhone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_system_id", nullable = false)
    private SourceSystem sourceSystem;

    @Column(nullable = false)
    private Instant createdAt;

    protected Customer() {
    }

    public Customer(
        String customerNo,
        String customerName,
        String customerPhone,
        SourceSystem sourceSystem,
        Instant createdAt
    ) {
        this.customerNo = customerNo;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.sourceSystem = sourceSystem;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerNo() {
        return customerNo;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public SourceSystem getSourceSystem() {
        return sourceSystem;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void clearPhone() {
        this.customerPhone = null;
    }
}
