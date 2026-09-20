package com.erpschool.tenant.dto;

import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.entity.TenantStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class TenantResponse {

    private final UUID id;
    private final String code;
    private final String name;
    private final String legalName;
    private final String email;
    private final String phone;
    private final String addressLine;
    private final String city;
    private final String state;
    private final String country;
    private final String postalCode;
    private final String logoUrl;
    private final String website;
    private final TenantStatus status;
    private final String academicYear;
    private final String academicSession;
    private final String timezone;
    private final String notes;
    private final Instant createdAt;
    private final Instant updatedAt;

    public static TenantResponse from(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .code(tenant.getCode())
                .name(tenant.getName())
                .legalName(tenant.getLegalName())
                .email(tenant.getEmail())
                .phone(tenant.getPhone())
                .addressLine(tenant.getAddressLine())
                .city(tenant.getCity())
                .state(tenant.getState())
                .country(tenant.getCountry())
                .postalCode(tenant.getPostalCode())
                .logoUrl(tenant.getLogoUrl())
                .website(tenant.getWebsite())
                .status(tenant.getStatus())
                .academicYear(tenant.getAcademicYear())
                .academicSession(tenant.getAcademicSession())
                .timezone(tenant.getTimezone())
                .notes(tenant.getNotes())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }
}
