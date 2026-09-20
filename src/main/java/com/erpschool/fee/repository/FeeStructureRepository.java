package com.erpschool.fee.repository;

import com.erpschool.fee.entity.FeeStructure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, UUID> {
    List<FeeStructure> findByTenantIdOrderByNameAsc(UUID tenantId);
    Optional<FeeStructure> findFirstByTenantIdAndClassId(UUID tenantId, UUID classId);
}
