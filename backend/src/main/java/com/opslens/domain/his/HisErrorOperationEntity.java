package com.opslens.domain.his;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "his_error_operation_record")
public class HisErrorOperationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long errorId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, columnDefinition = "text")
    private String memo;

    @Column(nullable = false, length = 1000)
    private String lastAnalysisSummary;

    @Column(nullable = false, columnDefinition = "text")
    private String lastAnalysisAnswer;

    private Instant analyzedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected HisErrorOperationEntity() {
    }

    public HisErrorOperationEntity(Long errorId, String status, String memo, Instant updatedAt) {
        this.errorId = errorId;
        this.status = status;
        this.memo = memo;
        this.lastAnalysisSummary = "";
        this.lastAnalysisAnswer = "";
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getErrorId() {
        return errorId;
    }

    public String getStatus() {
        return status;
    }

    public String getMemo() {
        return memo;
    }

    public String getLastAnalysisSummary() {
        return lastAnalysisSummary;
    }

    public String getLastAnalysisAnswer() {
        return lastAnalysisAnswer;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateOperation(String status, String memo, Instant updatedAt) {
        this.status = status;
        this.memo = memo;
        this.updatedAt = updatedAt;
    }

    public void updateAnalysis(String summary, String answer, Instant analyzedAt, Instant updatedAt) {
        this.lastAnalysisSummary = summary;
        this.lastAnalysisAnswer = answer;
        this.analyzedAt = analyzedAt;
        this.updatedAt = updatedAt;
        if ("OPEN".equals(this.status)) {
            this.status = "CHECKING";
        }
    }
}
