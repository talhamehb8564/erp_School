package com.erpschool.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTenantRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9]{2,20}$", message = "School code must be 2-20 alphanumeric characters")
    private String code;

    @NotBlank
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

    @Size(max = 200)
    private String website;

    @Size(max = 20)
    private String academicYear;

    @Size(max = 50)
    private String academicSession;

    @Size(max = 50)
    private String timezone;

    /**
     * Optional first school administrator created with the tenant.
     */
    @Size(max = 100)
    private String adminFirstName;

    @Size(max = 100)
    private String adminLastName;

    @Email
    @Size(max = 150)
    private String adminEmail;

    @Size(max = 30)
    private String adminPhone;
}
