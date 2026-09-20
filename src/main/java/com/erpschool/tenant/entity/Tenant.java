package com.erpschool.tenant.entity;

import com.erpschool.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(name = "address_line", length = 300)
    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(nullable = false, length = 100)
    private String country = "Pakistan";

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(length = 200)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Column(name = "academic_session", length = 50)
    private String academicSession;

    @Column(nullable = false, length = 50)
    private String timezone = "Asia/Karachi";

    @Column(columnDefinition = "TEXT")
    private String notes;

    public boolean allowsSchoolLogin() {
        return status == TenantStatus.ACTIVE || status == TenantStatus.PENDING;
    }
}
