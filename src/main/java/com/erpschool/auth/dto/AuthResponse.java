package com.erpschool.auth.dto;

import com.erpschool.tenant.dto.TenantResponse;
import com.erpschool.user.dto.UserResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder(toBuilder = true)
public class AuthResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long expiresIn;
    private final UserResponse user;
    private final TenantResponse tenant;
    private final Map<String, Boolean> features;
}
