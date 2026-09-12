package com.opslens.api.scenario;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.opslens.application.scenario.ScenarioInjectionService;
import com.opslens.application.scenario.ScenarioInjectionService.ScenarioDefinition;
import com.opslens.application.scenario.ScenarioInjectionService.ScenarioInjectionResult;
import com.opslens.domain.incident.AnomalyType;
import com.opslens.domain.scenario.ScenarioType;

@WebMvcTest(ScenarioController.class)
class ScenarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScenarioInjectionService scenarioInjectionService;

    @Test
    void returnsScenarioDefinitions() throws Exception {
        when(scenarioInjectionService.definitions())
            .thenReturn(List.of(new ScenarioDefinition(
                ScenarioType.ORDER_VOLUME_DROP,
                "Order volume drop",
                "Deletes 80% of orders and related payments for a source/date.",
                AnomalyType.COUNT_DROP
            )));

        mockMvc.perform(get("/api/scenarios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].scenarioType").value("ORDER_VOLUME_DROP"))
            .andExpect(jsonPath("$[0].expectedIncidentType").value("COUNT_DROP"));
    }

    @Test
    void injectsScenario() throws Exception {
        when(scenarioInjectionService.inject(any()))
            .thenReturn(new ScenarioInjectionResult(
                1L,
                ScenarioType.ORDER_VOLUME_DROP,
                "Order volume drop for SRC_02",
                "SRC_02",
                LocalDate.of(2026, 9, 13),
                8,
                AnomalyType.COUNT_DROP,
                "Orders from SRC_02 dropped because most records were not ingested.",
                Instant.parse("2026-09-13T00:00:00Z")
            ));

        mockMvc.perform(post("/api/scenarios/inject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "scenarioType": "ORDER_VOLUME_DROP",
                      "targetSourceCode": "SRC_02",
                      "targetDate": "2026-09-13"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scenarioId").value(1))
            .andExpect(jsonPath("$.scenarioType").value("ORDER_VOLUME_DROP"))
            .andExpect(jsonPath("$.targetSourceCode").value("SRC_02"))
            .andExpect(jsonPath("$.targetDate").value("2026-09-13"))
            .andExpect(jsonPath("$.affectedRows").value(8))
            .andExpect(jsonPath("$.expectedIncidentType").value("COUNT_DROP"));
    }
}
