package com.erpschool.portal.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.portal.service.PortalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/portals")
@Tag(name = "Portals")
public class PortalController {

    private final PortalService portalService;

    public PortalController(PortalService portalService) {
        this.portalService = portalService;
    }

    @GetMapping("/teacher")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> teacher() {
        return ResponseEntity.ok(ApiResponse.ok(portalService.teacher()));
    }

    @GetMapping("/principal")
    @PreAuthorize("hasAnyRole('PRINCIPAL','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> principal() {
        return ResponseEntity.ok(ApiResponse.ok(portalService.principal()));
    }

    @GetMapping("/account")
    @PreAuthorize("hasRole('ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> account() {
        return ResponseEntity.ok(ApiResponse.ok(portalService.account()));
    }

    @GetMapping("/parent")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> parent() {
        return ResponseEntity.ok(ApiResponse.ok(portalService.parent()));
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> student() {
        return ResponseEntity.ok(ApiResponse.ok(portalService.student()));
    }
}
