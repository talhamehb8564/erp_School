package com.erpschool.student.repository;

import com.erpschool.student.entity.ParentStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParentStudentRepository extends JpaRepository<ParentStudent, UUID> {

    List<ParentStudent> findByTenantIdAndParentUserId(UUID tenantId, UUID parentUserId);

    List<ParentStudent> findByTenantIdAndStudentId(UUID tenantId, UUID studentId);

    boolean existsByTenantIdAndParentUserIdAndStudentId(UUID tenantId, UUID parentUserId, UUID studentId);

    Optional<ParentStudent> findByTenantIdAndParentUserIdAndStudentId(UUID tenantId, UUID parentUserId, UUID studentId);
}
