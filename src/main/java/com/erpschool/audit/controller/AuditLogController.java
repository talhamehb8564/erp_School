package com.erpschool.audit.controller;

import com.erpschool.audit.dto.AuditLogResponse;
import com.erpschool.audit.repository.AuditLogRepository;
import com.erpschool.common.dto.ApiResponse;
import com.erpschool.common.dto.PageResponse;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    @Operation(summary = "Query audit logs. School admin is limited to their tenant.")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> list(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID scopedTenant = TenantContext.isErpOwner()
                ? tenantId
                : TenantGuard.requireTenantId(tenantId);
        boolean tenantPresent = scopedTenant != null;
        String actionValue = emptyToNull(action);
        String entityValue = emptyToNull(entityType);
        var page = auditLogRepository.search(
                        tenantPresent,
                        tenantPresent ? scopedTenant : UUID.fromString("00000000-0000-0000-0000-000000000000"),
                        actionValue != null,
                        actionValue == null ? "" : actionValue,
                        entityValue != null,
                        entityValue == null ? "" : entityValue,
                        pageable)
                .map(AuditLogResponse::from);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
