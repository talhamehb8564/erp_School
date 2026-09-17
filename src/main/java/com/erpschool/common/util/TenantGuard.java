package com.erpschool.common.util;

import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.tenant.context.TenantContext;

import java.util.UUID;

public final class TenantGuard {

    private TenantGuard() {
    }

    /**
     * School users are always locked to their JWT tenant.
     * ERP Owner must explicitly pass a tenant id for school-scoped operations.
     */
    public static UUID requireTenantId(UUID requestedTenantId) {
        if (TenantContext.isErpOwner()) {
            if (requestedTenantId == null) {
                throw new BusinessException("TENANT_REQUIRED", "tenantId is required for this operation");
            }
            return requestedTenantId;
        }
        UUID current = TenantContext.getTenantId();
        if (current == null) {
            throw new ForbiddenException("Tenant context is missing");
        }
        if (requestedTenantId != null && !requestedTenantId.equals(current)) {
            throw new ForbiddenException("Cannot access another school's data");
        }
        return current;
    }

    public static void assertSameTenant(UUID resourceTenantId) {
        if (TenantContext.isErpOwner()) {
            return;
        }
        UUID current = TenantContext.getTenantId();
        if (current == null || resourceTenantId == null || !current.equals(resourceTenantId)) {
            throw new ForbiddenException("Cannot access another school's data");
        }
    }
}
