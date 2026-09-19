package com.erpschool.fee.repository;

import com.erpschool.fee.entity.ChallanStatus;
import com.erpschool.fee.entity.FeeChallan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeChallanRepository extends JpaRepository<FeeChallan, UUID> {
    List<FeeChallan> findByTenantIdAndStudentIdOrderByMonthDesc(UUID tenantId, UUID studentId);
    Optional<FeeChallan> findByTenantIdAndStudentIdAndMonth(UUID tenantId, UUID studentId, LocalDate month);
    List<FeeChallan> findByTenantIdAndStatus(UUID tenantId, ChallanStatus status);
    List<FeeChallan> findByTenantIdAndMonth(UUID tenantId, LocalDate month);
    List<FeeChallan> findByTenantIdAndStudentIdAndStatusIn(UUID tenantId, UUID studentId, List<ChallanStatus> statuses);
    long countByTenantIdAndStatus(UUID tenantId, ChallanStatus status);

    List<FeeChallan> findByTenantIdAndStatusAndDueDateBefore(UUID tenantId, ChallanStatus status, LocalDate dueDate);
}
