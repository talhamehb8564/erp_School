package com.erpschool.fee.repository;

import com.erpschool.fee.entity.StudentFeeDiscount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentFeeDiscountRepository extends JpaRepository<StudentFeeDiscount, UUID> {
    List<StudentFeeDiscount> findByTenantIdAndStudentIdOrderByCreatedAtDesc(UUID tenantId, UUID studentId);

    Optional<StudentFeeDiscount> findFirstByTenantIdAndStudentIdAndActiveTrueOrderByCreatedAtDesc(
            UUID tenantId, UUID studentId);

    List<StudentFeeDiscount> findByTenantIdAndStudentIdInAndActiveTrue(UUID tenantId, Collection<UUID> studentIds);
}
