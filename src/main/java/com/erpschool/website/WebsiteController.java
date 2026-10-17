package com.erpschool.website;

import com.erpschool.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/website")
@Tag(name = "School website")
public class WebsiteController {

    private final WebsiteService websiteService;

    public WebsiteController(WebsiteService websiteService) {
        this.websiteService = websiteService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get() {
        return ResponseEntity.ok(ApiResponse.ok(websiteService.get()));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> save(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Website saved", websiteService.save(body)));
    }

    @PutMapping("/principal")
    @PreAuthorize("hasAnyRole('ERP_OWNER','PRINCIPAL')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> principal(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Principal profile saved", websiteService.savePrincipal(body)));
    }
}
