package com.erpschool.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTenantRequest {

    @Size(max = 200)
    private String name;

    @Size(max = 200)
    private String legalName;

    @Email
    @Size(max = 150)
    private String email;

    @Size(max = 30)
    private String phone;

    @Size(max = 300)
    private String addressLine;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String state;

    @Size(max = 100)
    private String country;

    @Size(max = 20)
    private String postalCode;

    @Size(max = 500)
    private String logoUrl;

    @Size(max = 200)
    private String website;

    @Size(max = 20)
    private String academicYear;

    @Size(max = 50)
    private String academicSession;

    @Size(max = 50)
    private String timezone;

    @Size(max = 2000)
    private String notes;
}
