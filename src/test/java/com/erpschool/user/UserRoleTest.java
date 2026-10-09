package com.erpschool.user;

import com.erpschool.user.entity.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoleTest {

    @Test
    void usernameCodesAreStable() {
        assertThat(UserRole.TEACHER.getUsernameCode()).isEqualTo("TCH");
        assertThat(UserRole.STUDENT.getUsernameCode()).isEqualTo("STU");
        assertThat(UserRole.PARENT.getUsernameCode()).isEqualTo("PAR");
        assertThat(UserRole.SCHOOL_ADMIN.getUsernameCode()).isEqualTo("ADM");
        assertThat(UserRole.ACCOUNT_OFFICER.getUsernameCode()).isEqualTo("ACC");
        assertThat(UserRole.PRINCIPAL.getUsernameCode()).isEqualTo("PRN");
    }

    @Test
    void onlyErpOwnerIsPlatformRole() {
        assertThat(UserRole.ERP_OWNER.isPlatformRole()).isTrue();
        assertThat(UserRole.SCHOOL_ADMIN.isPlatformRole()).isFalse();
        assertThat(UserRole.schoolRoles()).doesNotContain(UserRole.ERP_OWNER);
    }
}
