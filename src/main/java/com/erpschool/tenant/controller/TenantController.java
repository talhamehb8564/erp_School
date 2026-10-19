package com.erpschool.tenant.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.common.dto.PageResponse;
import com.erpschool.tenant.dto.CreateTenantRequest;
import com.erpschool.tenant.dto.CreateTenantResponse;
import com.erpschool.tenant.dto.TenantResponse;
import com.erpschool.tenant.dto.TenantStatusRequest;
import com.erpschool.tenant.dto.UpdateTenantRequest;
import com.erpschool.tenant.entity.TenantStatus;
import com.erpschool.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@Tag(name = "Tenants / Schools")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ERP_OWNER')")
    @Operation(summary = "Create a school tenant, optionally with the first school admin")
    public ResponseEntity<ApiResponse<CreateTenantResponse>> create(@Valid @RequestBody CreateTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("School created", tenantService.create(request)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ERP_OWNER')")
    public ResponseEntity<ApiResponse<PageResponse<TenantResponse>>> list(
            @RequestParam(required = false) TenantStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(tenantService.list(status, q, pageable)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN','PRINCIPAL','TEACHER','ACCOUNT_OFFICER','PARENT','STUDENT')")
    @Operation(summary = "Current school profile for the logged-in school user")
    public ResponseEntity<ApiResponse<TenantResponse>> me() {
        return ResponseEntity.ok(ApiResponse.ok(tenantService.me()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<TenantResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(tenantService.get(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<TenantResponse>> update(@PathVariable UUID id,
                                                              @Valid @RequestBody UpdateTenantRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("School updated", tenantService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ERP_OWNER')")
    @Operation(summary = "Activate, suspend, disable or expire a school")
    public ResponseEntity<ApiResponse<TenantResponse>> changeStatus(@PathVariable UUID id,
                                                                    @Valid @RequestBody TenantStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("School status updated", tenantService.changeStatus(id, request)));
    }
}
