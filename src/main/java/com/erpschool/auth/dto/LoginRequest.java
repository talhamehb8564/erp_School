package com.erpschool.auth.dto;

import com.erpschool.user.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    private UserRole expectedRole;

    private UserRole portalRole;

    public UserRole resolveExpectedRole() {
        return expectedRole != null ? expectedRole : portalRole;
    }
}
