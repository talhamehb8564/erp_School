package com.erpschool.auth.dto;

import com.erpschool.user.dto.UserResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long expiresIn;
    private final UserResponse user;
}
