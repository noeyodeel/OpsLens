package com.opslens.application.system;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseStatusService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseStatusService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DatabaseStatus check() {
        String databaseName = jdbcTemplate.queryForObject("select current_database()", String.class);
        String schemaVersion = jdbcTemplate.queryForObject(
            "select metadata_value from app_metadata where metadata_key = 'schema.version'",
            String.class
        );

        return new DatabaseStatus("UP", databaseName, schemaVersion, Instant.now());
    }

    public record DatabaseStatus(
        String status,
        String databaseName,
        String schemaVersion,
        Instant checkedAt
    ) {
    }
}
