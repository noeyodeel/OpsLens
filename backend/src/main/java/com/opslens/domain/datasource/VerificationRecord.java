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
@Table(name = "verification_records")
public class VerificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String verificationRecordKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treatment_record_id", nullable = false)
    private TreatmentRecord treatmentRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "external_institution_id", nullable = false)
    private ExternalInstitution externalInstitution;

    @Column(nullable = false, length = 30)
    private String verificationStatus;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant verifiedAt;

    protected VerificationRecord() {
    }

    public VerificationRecord(
        String verificationRecordKey,
        TreatmentRecord treatmentRecord,
        ExternalInstitution externalInstitution,
        String verificationStatus,
        BigDecimal amount,
        Instant verifiedAt
    ) {
        this.verificationRecordKey = verificationRecordKey;
        this.treatmentRecord = treatmentRecord;
        this.externalInstitution = externalInstitution;
        this.verificationStatus = verificationStatus;
        this.amount = amount;
        this.verifiedAt = verifiedAt;
    }

    public Long getId() {
        return id;
    }

    public String getVerificationRecordKey() {
        return verificationRecordKey;
    }

    public TreatmentRecord getTreatmentRecord() {
        return treatmentRecord;
    }

    public ExternalInstitution getExternalInstitution() {
        return externalInstitution;
    }

    public String getVerificationRecordStatus() {
        return verificationStatus;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void replaceVerificationRecordKey(String verificationRecordKey) {
        this.verificationRecordKey = verificationRecordKey;
    }
}
