package com.erpschool.tenant.context;

import com.erpschool.user.entity.UserRole;

import java.util.UUID;

/**
 * Request-scoped tenant + actor identity, populated from the JWT.
 * Never trust a client-supplied tenant id for school users.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<UserRole> ROLE = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CAMPUS_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId, UUID userId, String username, UserRole role) {
        set(tenantId, userId, username, role, null);
    }

    public static void set(UUID tenantId, UUID userId, String username, UserRole role, UUID campusId) {
        TENANT_ID.set(tenantId);
        USER_ID.set(userId);
        USERNAME.set(username);
        ROLE.set(role);
        CAMPUS_ID.set(campusId);
    }

    /** ERP Owner may pin a school for the request via X-Tenant-Id. */
    public static void overrideTenantId(UUID tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static UUID getTenantId() {
        return TENANT_ID.get();
    }

    public static UUID getUserId() {
        return USER_ID.get();
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    public static UserRole getRole() {
        return ROLE.get();
    }

    public static boolean isErpOwner() {
        return ROLE.get() == UserRole.ERP_OWNER;
    }

    public static UUID getCampusId() {
        return CAMPUS_ID.get();
    }

    public static boolean hasTenant() {
        return TENANT_ID.get() != null;
    }

    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
        USERNAME.remove();
        ROLE.remove();
        CAMPUS_ID.remove();
    }
}
