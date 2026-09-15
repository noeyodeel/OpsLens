package com.opslens.domain.datasource;

import java.time.Instant;
import java.time.LocalDate;

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
@Table(name = "data_ingestion_log")
public class DataIngestionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "external_institution_id", nullable = false)
    private ExternalInstitution externalInstitution;

    @Column(nullable = false, length = 50)
    private String targetTable;

    @Column(nullable = false)
    private LocalDate batchDate;

    @Column(nullable = false)
    private int receivedCount;

    @Column(nullable = false)
    private int successCount;

    @Column(nullable = false)
    private int failedCount;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant endedAt;

    protected DataIngestionLog() {
    }

    public DataIngestionLog(
        ExternalInstitution externalInstitution,
        String targetTable,
        LocalDate batchDate,
        int receivedCount,
        int successCount,
        int failedCount,
        String status,
        Instant startedAt,
        Instant endedAt
    ) {
        this.externalInstitution = externalInstitution;
        this.targetTable = targetTable;
        this.batchDate = batchDate;
        this.receivedCount = receivedCount;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public Long getId() {
        return id;
    }

    public ExternalInstitution getExternalInstitution() {
        return externalInstitution;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public LocalDate getBatchDate() {
        return batchDate;
    }

    public int getReceivedCount() {
        return receivedCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public String getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
