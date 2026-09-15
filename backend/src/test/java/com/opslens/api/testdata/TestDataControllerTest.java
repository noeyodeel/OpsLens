package com.opslens.api.testdata;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.opslens.application.testdata.TestDataGenerationService;
import com.opslens.application.testdata.TestDataGenerationService.TestDataGenerationResult;

@WebMvcTest(TestDataController.class)
class TestDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TestDataGenerationService testDataGenerationService;

    @Test
    void generatesTestData() throws Exception {
        when(testDataGenerationService.generate(any()))
            .thenReturn(new TestDataGenerationResult(
                2,
                6,
                16,
                16,
                4,
                2,
                7L,
                LocalDate.of(2026, 9, 13)
            ));

        mockMvc.perform(post("/api/test-data/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "days": 2,
                      "institutionCount": 2,
                      "subjectsPerInstitution": 3,
                      "recordsPerInstitutionPerDay": 4,
                      "seed": 7,
                      "baseDate": "2026-09-13",
                      "resetExisting": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.externalInstitutions").value(2))
            .andExpect(jsonPath("$.recordSubjects").value(6))
            .andExpect(jsonPath("$.treatmentRecords").value(16))
            .andExpect(jsonPath("$.verificationRecords").value(16))
            .andExpect(jsonPath("$.ingestionLogs").value(4))
            .andExpect(jsonPath("$.days").value(2))
            .andExpect(jsonPath("$.seed").value(7))
            .andExpect(jsonPath("$.baseDate").value("2026-09-13"));
    }

    @Test
    void rejectsInvalidGenerationOptions() throws Exception {
        mockMvc.perform(post("/api/test-data/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "days": 0
                    }
                    """))
            .andExpect(status().isBadRequest());
    }
}
