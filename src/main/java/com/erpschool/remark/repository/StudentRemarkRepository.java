package com.erpschool.remark.repository;

import com.erpschool.remark.entity.StudentRemark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StudentRemarkRepository extends JpaRepository<StudentRemark, UUID> {

    List<StudentRemark> findByTenantIdAndStudentIdOrderByRemarkDateDescCreatedAtDesc(UUID tenantId, UUID studentId);

    Page<StudentRemark> findByTenantIdAndStudentIdOrderByRemarkDateDescCreatedAtDesc(
            UUID tenantId, UUID studentId, Pageable pageable);

    Page<StudentRemark> findByTenantIdAndClassIdAndSectionIdAndRemarkDateBetweenOrderByRemarkDateDescCreatedAtDesc(
            UUID tenantId, UUID classId, UUID sectionId, LocalDate from, LocalDate to, Pageable pageable);

    Page<StudentRemark> findByTenantIdAndRemarkDateBetweenOrderByRemarkDateDescCreatedAtDesc(
            UUID tenantId, LocalDate from, LocalDate to, Pageable pageable);

    Page<StudentRemark> findByTenantIdAndCampusIdAndRemarkDateBetweenOrderByRemarkDateDescCreatedAtDesc(
            UUID tenantId, UUID campusId, LocalDate from, LocalDate to, Pageable pageable);
}
