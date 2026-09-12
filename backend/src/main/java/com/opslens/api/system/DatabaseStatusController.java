package com.opslens.api.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opslens.application.system.DatabaseStatusService;
import com.opslens.application.system.DatabaseStatusService.DatabaseStatus;

@RestController
public class DatabaseStatusController {

    private final DatabaseStatusService databaseStatusService;

    public DatabaseStatusController(DatabaseStatusService databaseStatusService) {
        this.databaseStatusService = databaseStatusService;
    }

    @GetMapping("/api/system/database")
    public DatabaseStatus databaseStatus() {
        return databaseStatusService.check();
    }
}
