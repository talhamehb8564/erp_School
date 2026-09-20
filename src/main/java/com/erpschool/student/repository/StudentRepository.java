package com.erpschool.student.repository;

import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Page<Student> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Student> findByTenantIdAndClassIdAndSectionId(UUID tenantId, UUID classId, UUID sectionId, Pageable pageable);

    List<Student> findByTenantIdAndClassIdAndSectionIdAndStatus(
            UUID tenantId, UUID classId, UUID sectionId, StudentStatus status);

    List<Student> findByTenantIdAndClassIdAndStatus(UUID tenantId, UUID classId, StudentStatus status);

    List<Student> findByTenantIdAndClassId(UUID tenantId, UUID classId);

    Optional<Student> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, StudentStatus status);

    long countByTenantIdAndClassId(UUID tenantId, UUID classId);
}
