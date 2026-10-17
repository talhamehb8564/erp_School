package com.erpschool.fee.repository;

import com.erpschool.fee.entity.FeeChargeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeeChargeTypeRepository extends JpaRepository<FeeChargeType, UUID> {
    List<FeeChargeType> findByTenantIdAndActiveTrueOrderByNameAsc(UUID tenantId);

    List<FeeChargeType> findByTenantIdOrderByNameAsc(UUID tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
}
