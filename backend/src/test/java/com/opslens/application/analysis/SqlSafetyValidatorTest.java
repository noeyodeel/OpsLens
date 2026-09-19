package com.opslens.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.opslens.application.analysis.IncidentAnalysisClient.VerificationSql;

class SqlSafetyValidatorTest {

    private final SqlSafetyValidator validator = new SqlSafetyValidator();

    @Test
    void allowsSingleSelectStatement() {
        VerificationSql checked = validator.validate(new VerificationSql(
            "Count records",
            "Verify count.",
            "select count(*) from treatment_records where recorded_at >= current_date"
        ));

        assertThat(checked.safe()).isTrue();
        assertThat(checked.safetyMessage()).isEqualTo("조회 전용 검증 SQL입니다.");
    }

    @Test
    void rejectsDataChangingStatement() {
        VerificationSql checked = validator.validate(new VerificationSql(
            "Bad SQL",
            "Should be blocked.",
            "update treatment_records set record_status = 'FIXED'"
        ));

        assertThat(checked.safe()).isFalse();
        assertThat(checked.safetyMessage()).isEqualTo("SELECT 또는 WITH 조회 쿼리만 허용됩니다.");
    }

    @Test
    void rejectsMultipleStatements() {
        VerificationSql checked = validator.validate(new VerificationSql(
            "Bad SQL",
            "Should be blocked.",
            "select * from treatment_records; drop table incident"
        ));

        assertThat(checked.safe()).isFalse();
        assertThat(checked.safetyMessage()).isEqualTo("여러 SQL 문을 한 번에 실행할 수 없습니다.");
    }

    @Test
    void rejectsForbiddenKeywordInsideSelect() {
        VerificationSql checked = validator.validate(new VerificationSql(
            "Bad SQL",
            "Should be blocked.",
            "select * from treatment_records for update"
        ));

        assertThat(checked.safe()).isFalse();
        assertThat(checked.safetyMessage()).isEqualTo("SELECT ... FOR UPDATE 구문은 허용되지 않습니다.");
    }

    @Test
    void validatesAllSqlItems() {
        List<VerificationSql> checked = validator.validateAll(List.of(
            new VerificationSql("Safe", "Safe.", "select 1"),
            new VerificationSql("Unsafe", "Unsafe.", "delete from incident")
        ));

        assertThat(checked).extracting(VerificationSql::safe)
            .containsExactly(true, false);
    }
}
