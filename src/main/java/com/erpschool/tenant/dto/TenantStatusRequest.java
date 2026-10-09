package com.erpschool.tenant.dto;

import com.erpschool.tenant.entity.TenantStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantStatusRequest {

    @NotNull
    private TenantStatus status;

    @Size(max = 500)
    private String reason;
}
