package com.erpschool.tenant.service;

import com.erpschool.audit.service.AuditService;
import com.erpschool.common.dto.PageResponse;
import com.erpschool.common.exception.DuplicateResourceException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.dto.CreateTenantRequest;
import com.erpschool.tenant.dto.CreateTenantResponse;
import com.erpschool.tenant.dto.TenantResponse;
import com.erpschool.tenant.dto.TenantStatusRequest;
import com.erpschool.tenant.dto.UpdateTenantRequest;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.entity.TenantStatus;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.dto.CreateUserResponse;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.service.UserService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserService userService;
    private final AuditService auditService;

    public TenantService(TenantRepository tenantRepository,
                         UserService userService,
                         AuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.userService = userService;
        this.auditService = auditService;
    }

    @Transactional
    public CreateTenantResponse create(CreateTenantRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (tenantRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("School code already exists: " + code);
        }
        Tenant tenant = new Tenant();
        tenant.setCode(code);
        tenant.setName(request.getName().trim());
        tenant.setLegalName(trimToNull(request.getLegalName()));
        tenant.setEmail(trimToNull(request.getEmail()));
        tenant.setPhone(trimToNull(request.getPhone()));
        tenant.setAddressLine(trimToNull(request.getAddressLine()));
        tenant.setCity(trimToNull(request.getCity()));
        tenant.setState(trimToNull(request.getState()));
        tenant.setCountry(request.getCountry() == null || request.getCountry().isBlank()
                ? "Pakistan" : request.getCountry().trim());
        tenant.setPostalCode(trimToNull(request.getPostalCode()));
        tenant.setWebsite(trimToNull(request.getWebsite()));
        tenant.setAcademicYear(trimToNull(request.getAcademicYear()));
        tenant.setAcademicSession(trimToNull(request.getAcademicSession()));
        tenant.setTimezone(request.getTimezone() == null || request.getTimezone().isBlank()
                ? "Asia/Karachi" : request.getTimezone().trim());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setCreatedBy(TenantContext.getUserId());
        tenant = tenantRepository.save(tenant);

        CreateUserResponse admin = null;
        if (hasText(request.getAdminFirstName()) && hasText(request.getAdminLastName())) {
            admin = userService.createInternal(
                    tenant.getId(),
                    tenant.getCode(),
                    UserRole.SCHOOL_ADMIN,
                    request.getAdminFirstName(),
                    request.getAdminLastName(),
                    request.getAdminEmail(),
                    request.getAdminPhone(),
                    null,
                    true
            );
        }

        auditService.record(AuditService.TENANT_CREATED, "Tenant", tenant.getId().toString(),
                Map.of("code", tenant.getCode(), "name", tenant.getName()));

        return CreateTenantResponse.builder()
                .tenant(TenantResponse.from(tenant))
                .administrator(admin)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantResponse> list(TenantStatus status, String q, Pageable pageable) {
        return PageResponse.from(
                tenantRepository.search(status, trimToNull(q), pageable).map(TenantResponse::from));
    }

    @Transactional(readOnly = true)
    public TenantResponse get(UUID id) {
        return TenantResponse.from(findVisible(id));
    }

    @Transactional(readOnly = true)
    public TenantResponse me() {
        if (TenantContext.isErpOwner() || TenantContext.getTenantId() == null) {
            throw new ForbiddenException("ERP owner is not bound to a school. Use GET /api/v1/tenants/{id}");
        }
        return TenantResponse.from(findVisible(TenantContext.getTenantId()));
    }

    @Transactional
    public TenantResponse update(UUID id, UpdateTenantRequest request) {
        Tenant tenant = findVisible(id);
        if (!TenantContext.isErpOwner()) {
            TenantGuard.assertSameTenant(tenant.getId());
        }
        if (request.getName() != null) {
            tenant.setName(request.getName().trim());
        }
        if (request.getLegalName() != null) {
            tenant.setLegalName(trimToNull(request.getLegalName()));
        }
        if (request.getEmail() != null) {
            tenant.setEmail(trimToNull(request.getEmail()));
        }
        if (request.getPhone() != null) {
            tenant.setPhone(trimToNull(request.getPhone()));
        }
        if (request.getAddressLine() != null) {
            tenant.setAddressLine(trimToNull(request.getAddressLine()));
        }
        if (request.getCity() != null) {
            tenant.setCity(trimToNull(request.getCity()));
        }
        if (request.getState() != null) {
            tenant.setState(trimToNull(request.getState()));
        }
        if (request.getCountry() != null) {
            tenant.setCountry(trimToNull(request.getCountry()));
        }
        if (request.getPostalCode() != null) {
            tenant.setPostalCode(trimToNull(request.getPostalCode()));
        }
        if (request.getLogoUrl() != null) {
            tenant.setLogoUrl(trimToNull(request.getLogoUrl()));
        }
        if (request.getWebsite() != null) {
            tenant.setWebsite(trimToNull(request.getWebsite()));
        }
        if (request.getAcademicYear() != null) {
            tenant.setAcademicYear(trimToNull(request.getAcademicYear()));
        }
        if (request.getAcademicSession() != null) {
            tenant.setAcademicSession(trimToNull(request.getAcademicSession()));
        }
        if (request.getTimezone() != null) {
            tenant.setTimezone(trimToNull(request.getTimezone()));
        }
        if (request.getNotes() != null && TenantContext.isErpOwner()) {
            tenant.setNotes(trimToNull(request.getNotes()));
        }
        tenant.setUpdatedBy(TenantContext.getUserId());
        tenant = tenantRepository.save(tenant);
        auditService.record(AuditService.TENANT_UPDATED, "Tenant", tenant.getId().toString(),
                Map.of("code", tenant.getCode()));
        return TenantResponse.from(tenant);
    }

    @Transactional
    public TenantResponse changeStatus(UUID id, TenantStatusRequest request) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", id));
        TenantStatus previous = tenant.getStatus();
        tenant.setStatus(request.getStatus());
        tenant.setUpdatedBy(TenantContext.getUserId());
        tenant = tenantRepository.save(tenant);
        auditService.record(AuditService.TENANT_STATUS_CHANGED, "Tenant", tenant.getId().toString(),
                Map.of("from", previous.name(), "to", request.getStatus().name(),
                        "reason", request.getReason() == null ? "" : request.getReason()));
        return TenantResponse.from(tenant);
    }

    private Tenant findVisible(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", id));
        if (!TenantContext.isErpOwner()) {
            TenantGuard.assertSameTenant(tenant.getId());
        }
        return tenant;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
