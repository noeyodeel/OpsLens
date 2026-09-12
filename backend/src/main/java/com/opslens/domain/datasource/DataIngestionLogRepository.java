package com.opslens.domain.datasource;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DataIngestionLogRepository extends JpaRepository<DataIngestionLog, Long> {

    List<DataIngestionLog> findByTargetTableAndBatchDate(String targetTable, LocalDate batchDate);
}
