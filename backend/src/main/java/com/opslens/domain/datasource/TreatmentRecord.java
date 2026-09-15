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
@Table(name = "treatment_records")
public class TreatmentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String treatmentRecordNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_subject_id", nullable = false)
    private RecordSubject recordSubject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "external_institution_id", nullable = false)
    private ExternalInstitution externalInstitution;

    @Column(nullable = false, length = 30)
    private String recordStatus;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal recordValue;

    @Column(nullable = false)
    private Instant recordedAt;

    @Column(nullable = false)
    private Instant ingestedAt;

    protected TreatmentRecord() {
    }

    public TreatmentRecord(
        String treatmentRecordNo,
        RecordSubject recordSubject,
        ExternalInstitution externalInstitution,
        String recordStatus,
        BigDecimal recordValue,
        Instant recordedAt,
        Instant ingestedAt
    ) {
        this.treatmentRecordNo = treatmentRecordNo;
        this.recordSubject = recordSubject;
        this.externalInstitution = externalInstitution;
        this.recordStatus = recordStatus;
        this.recordValue = recordValue;
        this.recordedAt = recordedAt;
        this.ingestedAt = ingestedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTreatmentRecordNo() {
        return treatmentRecordNo;
    }

    public RecordSubject getRecordSubject() {
        return recordSubject;
    }

    public ExternalInstitution getExternalInstitution() {
        return externalInstitution;
    }

    public String getRecordStatus() {
        return recordStatus;
    }

    public BigDecimal getRecordValue() {
        return recordValue;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
