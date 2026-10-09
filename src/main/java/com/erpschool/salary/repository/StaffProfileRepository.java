package com.erpschool.salary.repository;

import com.erpschool.salary.entity.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, UUID> {
    Optional<StaffProfile> findByTenantIdAndUserId(UUID tenantId, UUID userId);
    List<StaffProfile> findByTenantId(UUID tenantId);
}
