package com.erpschool.feature;

import com.erpschool.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "School features")
public class FeatureController {

    private final FeatureService featureService;

    public FeatureController(FeatureService featureService) {
        this.featureService = featureService;
    }

    @GetMapping("/features")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> current() {
        return ResponseEntity.ok(ApiResponse.ok(featureService.current()));
    }

    @GetMapping("/tenants/{id}/features")
    @PreAuthorize("hasRole('ERP_OWNER')")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> forTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(featureService.forTenant(id)));
    }

    @PutMapping("/tenants/{id}/features")
    @PreAuthorize("hasRole('ERP_OWNER')")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> replace(
            @PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        return ResponseEntity.ok(ApiResponse.ok("Features updated", featureService.replace(id, body)));
    }
}
