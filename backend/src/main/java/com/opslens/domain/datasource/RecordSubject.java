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
@Table(name = "record_subjects")
public class RecordSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String subjectNo;

    @Column(nullable = false, length = 100)
    private String subjectAlias;

    @Column(length = 30)
    private String requiredFieldValue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "external_institution_id", nullable = false)
    private ExternalInstitution externalInstitution;

    @Column(nullable = false)
    private Instant createdAt;

    protected RecordSubject() {
    }

    public RecordSubject(
        String subjectNo,
        String subjectAlias,
        String requiredFieldValue,
        ExternalInstitution externalInstitution,
        Instant createdAt
    ) {
        this.subjectNo = subjectNo;
        this.subjectAlias = subjectAlias;
        this.requiredFieldValue = requiredFieldValue;
        this.externalInstitution = externalInstitution;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getRecordSubjectNo() {
        return subjectNo;
    }

    public String getSubjectAlias() {
        return subjectAlias;
    }

    public String getRequiredFieldValue() {
        return requiredFieldValue;
    }

    public ExternalInstitution getExternalInstitution() {
        return externalInstitution;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void clearRequiredField() {
        this.requiredFieldValue = null;
    }
}
