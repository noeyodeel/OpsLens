package com.opslens.api.system;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.opslens.application.system.DatabaseStatusService;
import com.opslens.application.system.DatabaseStatusService.DatabaseStatus;

@WebMvcTest(DatabaseStatusController.class)
class DatabaseStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseStatusService databaseStatusService;

    @Test
    void returnsDatabaseStatus() throws Exception {
        when(databaseStatusService.check())
            .thenReturn(new DatabaseStatus("UP", "opslens", "1", Instant.parse("2026-09-13T00:00:00Z")));

        mockMvc.perform(get("/api/system/database"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.databaseName").value("opslens"))
            .andExpect(jsonPath("$.schemaVersion").value("1"))
            .andExpect(jsonPath("$.checkedAt", notNullValue()));
    }
}
