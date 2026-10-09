package com.erpschool.tenant.dto;

import com.erpschool.user.dto.CreateUserResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreateTenantResponse {

    private final TenantResponse tenant;
    private final CreateUserResponse administrator;
}
