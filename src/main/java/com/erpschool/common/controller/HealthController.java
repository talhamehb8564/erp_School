package com.erpschool.common.controller;

import com.erpschool.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health")
public class HealthController {

    @GetMapping
    @Operation(summary = "Liveness probe")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "status", "UP",
                "phase", "1",
                "service", "erp-school",
                "time", Instant.now().toString()
        )));
    }
}
