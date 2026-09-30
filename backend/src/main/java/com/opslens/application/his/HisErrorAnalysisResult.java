package com.opslens.application.his;

import java.util.List;

public record HisErrorAnalysisResult(
    long errorId,
    String question,
    String answer,
    String summary,
    String suspectedCause,
    List<String> checkSteps,
    List<String> fixSteps,
    List<KnowledgeDocument> documents,
    List<KnowledgeSnippet> snippets,
    String generatedAt,
    String mode
) {
    public record KnowledgeDocument(
        String id,
        String title,
        String fileName,
        String kind,
        List<String> tags,
        List<String> relatedProcedures,
        List<String> relatedTables,
        String description
    ) {
    }

    public record KnowledgeSnippet(
        String id,
        String documentId,
        String title,
        String content,
        List<String> keywords
    ) {
    }
}
