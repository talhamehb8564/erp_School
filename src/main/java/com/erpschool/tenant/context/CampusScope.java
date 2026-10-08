package com.erpschool.tenant.context;

import com.erpschool.user.entity.UserRole;

import java.util.UUID;

/**
 * Branch (campus) isolation for school staff. ERP Owner and users with a null
 * campus_id remain tenant-wide. Tenant filter is never disabled.
 */
public final class CampusScope {

    private CampusScope() {
    }

    public static UUID current() {
        return TenantContext.getCampusId();
    }

    public static boolean restricts() {
        UserRole role = TenantContext.getRole();
        if (role == null || role == UserRole.ERP_OWNER) {
            return false;
        }
        return TenantContext.getCampusId() != null;
    }
}
