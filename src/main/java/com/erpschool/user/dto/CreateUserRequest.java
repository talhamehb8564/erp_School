package com.erpschool.user.dto;

import com.erpschool.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateUserRequest {

    /**
     * Required for ERP Owner creating a school user. Ignored for school admins
     * (their JWT tenant is always used).
     */
    private UUID tenantId;

    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @Email
    @Size(max = 150)
    private String email;

    @Size(max = 30)
    private String phone;

    @NotNull
    private UserRole role;
}
