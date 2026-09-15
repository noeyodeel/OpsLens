package com.opslens.api.testdata;

import java.time.LocalDate;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opslens.application.testdata.TestDataGenerationService;
import com.opslens.application.testdata.TestDataGenerationService.TestDataGenerationCommand;
import com.opslens.application.testdata.TestDataGenerationService.TestDataGenerationResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/test-data")
public class TestDataController {

    private final TestDataGenerationService testDataGenerationService;

    public TestDataController(TestDataGenerationService testDataGenerationService) {
        this.testDataGenerationService = testDataGenerationService;
    }

    @PostMapping("/generate")
    public TestDataGenerationResult generate(@Valid @RequestBody(required = false) GenerateTestDataRequest request) {
        GenerateTestDataRequest safeRequest = request == null ? GenerateTestDataRequest.empty() : request;
        return testDataGenerationService.generate(safeRequest.toCommand());
    }

    public record GenerateTestDataRequest(
        @Min(1) @Max(30) Integer days,
        @Min(1) @Max(10) Integer institutionCount,
        @Min(1) @Max(500) Integer subjectsPerInstitution,
        @Min(1) @Max(2_000) Integer recordsPerInstitutionPerDay,
        Long seed,
        LocalDate baseDate,
        Boolean resetExisting
    ) {

        private static GenerateTestDataRequest empty() {
            return new GenerateTestDataRequest(null, null, null, null, null, null, null);
        }

        private TestDataGenerationCommand toCommand() {
            return new TestDataGenerationCommand(
                days,
                institutionCount,
                subjectsPerInstitution,
                recordsPerInstitutionPerDay,
                seed,
                baseDate,
                resetExisting
            );
        }
    }
}
