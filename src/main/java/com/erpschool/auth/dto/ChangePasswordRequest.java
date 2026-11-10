package com.erpschool.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @Size(max = 72, message = "Password must be at most 72 characters")
    private String newPassword;

    @Size(min = 3, max = 80)
    private String newUsername;
}
