package com.opslens.api.incident;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.opslens.application.detection.VolumeDropDetectionService;
import com.opslens.application.detection.VolumeDropDetectionService.VolumeDropDetectionResult;
import com.opslens.application.detection.RequiredFieldNullSpikeDetectionService;
import com.opslens.application.detection.RequiredFieldNullSpikeDetectionService.RequiredFieldNullSpikeDetectionResult;

@WebMvcTest(IncidentDetectionController.class)
class IncidentDetectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VolumeDropDetectionService volumeDropDetectionService;

    @MockitoBean
    private RequiredFieldNullSpikeDetectionService requiredFieldNullSpikeDetectionService;

    @Test
    void detectsVolumeDropIncidents() throws Exception {
        when(volumeDropDetectionService.detect(any()))
            .thenReturn(List.of(new VolumeDropDetectionResult(
                "INST_02",
                LocalDate.of(2026, 9, 13),
                new BigDecimal("40.0000"),
                new BigDecimal("8.0000"),
                new BigDecimal("-80.0000"),
                true,
                "INC-20260913-VOLUME-DROP-INST_02"
            )));
        when(requiredFieldNullSpikeDetectionService.detect(any()))
            .thenReturn(List.of(new RequiredFieldNullSpikeDetectionResult(
                "INST_03",
                LocalDate.of(2026, 9, 13),
                new BigDecimal("0.0000"),
                new BigDecimal("80.0000"),
                new BigDecimal("80.0000"),
                10,
                8,
                true,
                "INC-20260913-NULL-SPIKE-INST_03"
            )));

        mockMvc.perform(post("/api/incidents/detect")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetDate": "2026-09-13"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].detectorType").value("VOLUME_DROP"))
            .andExpect(jsonPath("$[0].targetInstitutionCode").value("INST_02"))
            .andExpect(jsonPath("$[0].targetDate").value("2026-09-13"))
            .andExpect(jsonPath("$[0].targetTable").value("treatment_records"))
            .andExpect(jsonPath("$[0].baselineValue").value(40.0000))
            .andExpect(jsonPath("$[0].currentValue").value(8.0000))
            .andExpect(jsonPath("$[0].changeRate").value(-80.0000))
            .andExpect(jsonPath("$[0].incidentCreated").value(true))
            .andExpect(jsonPath("$[0].incidentNo").value("INC-20260913-VOLUME-DROP-INST_02"))
            .andExpect(jsonPath("$[1].detectorType").value("REQUIRED_FIELD_NULL_SPIKE"))
            .andExpect(jsonPath("$[1].targetInstitutionCode").value("INST_03"))
            .andExpect(jsonPath("$[1].targetTable").value("record_subjects"))
            .andExpect(jsonPath("$[1].baselineValue").value(0.0000))
            .andExpect(jsonPath("$[1].currentValue").value(80.0000))
            .andExpect(jsonPath("$[1].incidentNo").value("INC-20260913-NULL-SPIKE-INST_03"));
    }
}
