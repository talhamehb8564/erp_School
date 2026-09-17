package com.erpschool.common.entity;

import java.util.UUID;

/**
 * Marker for entities that belong to a school tenant.
 * Queries against these entities MUST always be scoped by tenantId.
 */
public interface TenantAware {

    UUID getTenantId();

    void setTenantId(UUID tenantId);
}
