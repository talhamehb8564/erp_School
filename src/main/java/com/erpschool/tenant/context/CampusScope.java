package com.erpschool.tenant.context;

import com.erpschool.user.entity.UserRole;

import java.util.UUID;

/**
 * Branch (campus) isolation for school staff. ERP Owner and Principal remain
 * tenant-wide (all campuses). School Admin, Teacher and Account Officer are
 * limited to their JWT campus when campus_id is set. Tenant filter is never disabled.
 */
public final class CampusScope {

    private CampusScope() {
    }

    public static UUID current() {
        return TenantContext.getCampusId();
    }

    public static boolean restricts() {
        UserRole role = TenantContext.getRole();
        if (role == null || role == UserRole.ERP_OWNER || role == UserRole.PRINCIPAL) {
            return false;
        }
        if (role == UserRole.SCHOOL_ADMIN || role == UserRole.TEACHER || role == UserRole.ACCOUNT_OFFICER) {
            return TenantContext.getCampusId() != null;
        }
        return false;
    }
}
