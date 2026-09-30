package com.opslens.application.his;

import java.util.List;

public record HisErrorOperationRecord(
    long errorId,
    String status,
    String memo,
    String lastAnalysisSummary,
    String lastAnalysisAnswer,
    String analyzedAt,
    String updatedAt,
    List<SimilarHistory> similarHistories
) {
    public record SimilarHistory(
        long errorId,
        String errorDt,
        String procName,
        String errorCode,
        String status,
        String memo,
        String reason
    ) {
    }
}
