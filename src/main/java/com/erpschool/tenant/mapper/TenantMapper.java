package com.erpschool.tenant.mapper;

import com.erpschool.tenant.dto.TenantResponse;
import com.erpschool.tenant.entity.Tenant;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TenantMapper {

    TenantResponse toResponse(Tenant tenant);
}
