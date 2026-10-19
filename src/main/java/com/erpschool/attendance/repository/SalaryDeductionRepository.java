package com.erpschool.attendance.repository;

import com.erpschool.attendance.entity.SalaryDeduction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SalaryDeductionRepository extends JpaRepository<SalaryDeduction, UUID> {
    List<SalaryDeduction> findByTenantIdAndTeacherUserIdAndMonth(UUID tenantId, UUID teacherUserId, LocalDate month);
}
