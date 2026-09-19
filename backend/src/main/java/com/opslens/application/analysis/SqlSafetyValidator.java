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
            return SafetyDecision.unsafe("SQL이 비어 있습니다.");
        }

        String normalized = stripComments(sql).trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("select") && !lower.startsWith("with")) {
            return SafetyDecision.unsafe("SELECT 또는 WITH 조회 쿼리만 허용됩니다.");
        }
        if (hasMultipleStatements(normalized)) {
            return SafetyDecision.unsafe("여러 SQL 문을 한 번에 실행할 수 없습니다.");
        }
        if (FOR_UPDATE.matcher(lower).find()) {
            return SafetyDecision.unsafe("SELECT ... FOR UPDATE 구문은 허용되지 않습니다.");
        }
        if (FORBIDDEN_KEYWORDS.matcher(lower).find()) {
            return SafetyDecision.unsafe("데이터 변경 또는 관리 작업 키워드가 포함되어 있습니다.");
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
            return new SafetyDecision(true, "조회 전용 검증 SQL입니다.");
        }

        private static SafetyDecision unsafe(String message) {
            return new SafetyDecision(false, message);
        }
    }
}
