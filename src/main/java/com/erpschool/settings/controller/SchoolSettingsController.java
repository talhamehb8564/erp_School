package com.erpschool.settings.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.settings.entity.SchoolSettings;
import com.erpschool.settings.service.SchoolSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/school-settings")
@Tag(name = "School settings")
public class SchoolSettingsController {

    private final SchoolSettingsService schoolSettingsService;

    public SchoolSettingsController(SchoolSettingsService schoolSettingsService) {
        this.schoolSettingsService = schoolSettingsService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SchoolSettings>> get() {
        return ResponseEntity.ok(ApiResponse.ok(schoolSettingsService.get()));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<SchoolSettings>> save(@RequestBody SchoolSettings body) {
        return ResponseEntity.ok(ApiResponse.ok("School settings saved", schoolSettingsService.save(body)));
    }
}
