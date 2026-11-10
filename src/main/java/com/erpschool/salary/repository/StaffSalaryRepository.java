package com.erpschool.salary.repository;

import com.erpschool.salary.entity.StaffSalary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffSalaryRepository extends JpaRepository<StaffSalary, UUID> {
    List<StaffSalary> findByTenantIdAndMonth(UUID tenantId, LocalDate month);
    Optional<StaffSalary> findByTenantIdAndStaffUserIdAndMonth(UUID tenantId, UUID staffUserId, LocalDate month);
    List<StaffSalary> findByTenantIdAndStaffUserIdOrderByMonthDesc(UUID tenantId, UUID staffUserId);
}
