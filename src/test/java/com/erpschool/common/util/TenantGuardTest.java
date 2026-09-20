package com.erpschool.common.util;

import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantGuardTest {

    private final UUID schoolA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID schoolB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void schoolAdminCannotSwitchTenant() {
        TenantContext.set(schoolA, UUID.randomUUID(), "GVS-ADM-0001", UserRole.SCHOOL_ADMIN);
        assertThat(TenantGuard.requireTenantId(null)).isEqualTo(schoolA);
        assertThatThrownBy(() -> TenantGuard.requireTenantId(schoolB))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void erpOwnerMustPassTenantId() {
        TenantContext.set(null, UUID.randomUUID(), "erp.owner", UserRole.ERP_OWNER);
        assertThatThrownBy(() -> TenantGuard.requireTenantId(null))
                .isInstanceOf(BusinessException.class);
        assertThat(TenantGuard.requireTenantId(schoolA)).isEqualTo(schoolA);
    }

    @Test
    void erpOwnerCanUsePinnedTenantFromContext() {
        TenantContext.set(null, UUID.randomUUID(), "erp.owner", UserRole.ERP_OWNER);
        TenantContext.overrideTenantId(schoolA);
        assertThat(TenantGuard.requireTenantId(null)).isEqualTo(schoolA);
    }

    @Test
    void schoolUserCannotReadForeignTenant() {
        TenantContext.set(schoolA, UUID.randomUUID(), "GVS-TCH-0001", UserRole.TEACHER);
        assertThatThrownBy(() -> TenantGuard.assertSameTenant(schoolB))
                .isInstanceOf(ForbiddenException.class);
        TenantGuard.assertSameTenant(schoolA);
    }
}
