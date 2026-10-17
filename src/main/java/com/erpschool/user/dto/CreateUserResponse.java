package com.erpschool.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreateUserResponse {

    private final UserResponse user;
    private final String temporaryPassword;
    private final String message;
}
