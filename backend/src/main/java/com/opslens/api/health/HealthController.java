package com.opslens.api.health;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "OpsLens backend is running", Instant.now());
    }

    public record HealthResponse(String status, String message, Instant checkedAt) {
    }
}
