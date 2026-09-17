package com.erpschool.user.entity;

import java.util.EnumSet;
import java.util.Set;

public enum UserRole {
    ERP_OWNER("OWN"),
    SCHOOL_ADMIN("ADM"),
    PRINCIPAL("PRN"),
    TEACHER("TCH"),
    ACCOUNT_OFFICER("ACC"),
    PARENT("PAR"),
    STUDENT("STU");

    private final String usernameCode;

    UserRole(String usernameCode) {
        this.usernameCode = usernameCode;
    }

    public String getUsernameCode() {
        return usernameCode;
    }

    public boolean isPlatformRole() {
        return this == ERP_OWNER;
    }

    public static Set<UserRole> schoolRoles() {
        return EnumSet.complementOf(EnumSet.of(ERP_OWNER));
    }

    public static Set<UserRole> userManagers() {
        return EnumSet.of(ERP_OWNER, SCHOOL_ADMIN);
    }
}
