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
        assertThat(checked.safetyMessage()).isEqualTo("SELECT-only verification SQL.");
    }

    @Test
    void rejectsDataChangingStatement() {
        VerificationSql checked = validator.validate(new VerificationSql(
            "Bad SQL",
            "Should be blocked.",
            "update treatment_records set record_status = 'FIXED'"
        ));

        assertThat(checked.safe()).isFalse();
        assertThat(checked.safetyMessage()).isEqualTo("Only SELECT queries are allowed.");
    }

    @Test
    void rejectsMultipleStatements() {
        VerificationSql checked = validator.validate(new VerificationSql(
            "Bad SQL",
            "Should be blocked.",
            "select * from treatment_records; drop table incident"
        ));

        assertThat(checked.safe()).isFalse();
        assertThat(checked.safetyMessage()).isEqualTo("Multiple SQL statements are not allowed.");
    }

    @Test
    void rejectsForbiddenKeywordInsideSelect() {
        VerificationSql checked = validator.validate(new VerificationSql(
            "Bad SQL",
            "Should be blocked.",
            "select * from treatment_records for update"
        ));

        assertThat(checked.safe()).isFalse();
        assertThat(checked.safetyMessage()).isEqualTo("SELECT ... FOR UPDATE is not allowed.");
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
