package com.opslens.api.scenario;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opslens.application.scenario.ScenarioInjectionService;
import com.opslens.application.scenario.ScenarioInjectionService.ScenarioDefinition;
import com.opslens.application.scenario.ScenarioInjectionService.ScenarioInjectionCommand;
import com.opslens.application.scenario.ScenarioInjectionService.ScenarioInjectionResult;
import com.opslens.domain.scenario.ScenarioType;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    private final ScenarioInjectionService scenarioInjectionService;

    public ScenarioController(ScenarioInjectionService scenarioInjectionService) {
        this.scenarioInjectionService = scenarioInjectionService;
    }

    @GetMapping
    public List<ScenarioDefinition> definitions() {
        return scenarioInjectionService.definitions();
    }

    @PostMapping("/inject")
    public ScenarioInjectionResult inject(@Valid @RequestBody(required = false) InjectScenarioRequest request) {
        InjectScenarioRequest safeRequest = request == null ? InjectScenarioRequest.empty() : request;
        return scenarioInjectionService.inject(safeRequest.toCommand());
    }

    public record InjectScenarioRequest(
        ScenarioType scenarioType,
        String targetInstitutionCode,
        LocalDate targetDate
    ) {

        private static InjectScenarioRequest empty() {
            return new InjectScenarioRequest(null, null, null);
        }

        private ScenarioInjectionCommand toCommand() {
            return new ScenarioInjectionCommand(scenarioType, targetInstitutionCode, targetDate);
        }
    }
}
