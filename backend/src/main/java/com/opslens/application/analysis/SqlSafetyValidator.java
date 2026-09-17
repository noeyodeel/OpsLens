package com.opslens.application.analysis;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.opslens.application.analysis.IncidentAnalysisClient.VerificationSql;

@Component
public class SqlSafetyValidator {

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("--.*?(\\R|$)");
    private static final Pattern FORBIDDEN_KEYWORDS = Pattern.compile(
        "\\b(insert|update|delete|drop|alter|truncate|create|merge|grant|revoke|call|execute|copy|vacuum|analyze)\\b"
    );
    private static final Pattern FOR_UPDATE = Pattern.compile("\\bfor\\s+update\\b");

    public List<VerificationSql> validateAll(List<VerificationSql> verificationSql) {
        return verificationSql.stream()
            .map(this::validate)
            .toList();
    }

    public VerificationSql validate(VerificationSql verificationSql) {
        SafetyDecision decision = decide(verificationSql.sql());
        return new VerificationSql(
            verificationSql.title(),
            verificationSql.purpose(),
            verificationSql.sql(),
            decision.safe(),
            decision.message()
        );
    }

    private SafetyDecision decide(String sql) {
        if (sql == null || sql.isBlank()) {
            return SafetyDecision.unsafe("SQL is blank.");
        }

        String normalized = stripComments(sql).trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("select") && !lower.startsWith("with")) {
            return SafetyDecision.unsafe("Only SELECT queries are allowed.");
        }
        if (hasMultipleStatements(normalized)) {
            return SafetyDecision.unsafe("Multiple SQL statements are not allowed.");
        }
        if (FOR_UPDATE.matcher(lower).find()) {
            return SafetyDecision.unsafe("SELECT ... FOR UPDATE is not allowed.");
        }
        if (FORBIDDEN_KEYWORDS.matcher(lower).find()) {
            return SafetyDecision.unsafe("SQL contains a forbidden data-changing or administrative keyword.");
        }
        return SafetyDecision.allowed();
    }

    private String stripComments(String sql) {
        String withoutBlockComments = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
        return LINE_COMMENT.matcher(withoutBlockComments).replaceAll(" ");
    }

    private boolean hasMultipleStatements(String sql) {
        String trimmed = sql.trim();
        int firstSemicolon = trimmed.indexOf(';');
        return firstSemicolon >= 0 && firstSemicolon != trimmed.length() - 1;
    }

    private record SafetyDecision(boolean safe, String message) {

        private static SafetyDecision allowed() {
            return new SafetyDecision(true, "SELECT-only verification SQL.");
        }

        private static SafetyDecision unsafe(String message) {
            return new SafetyDecision(false, message);
        }
    }
}
