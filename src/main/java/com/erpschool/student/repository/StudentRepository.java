package com.erpschool.student.repository;

import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Page<Student> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Student> findByTenantIdAndCampusId(UUID tenantId, UUID campusId, Pageable pageable);

    Page<Student> findByTenantIdAndCampusIdAndClassId(UUID tenantId, UUID campusId, UUID classId, Pageable pageable);

    long countByTenantIdAndCampusId(UUID tenantId, UUID campusId);

    long countByTenantIdAndCampusIdAndStatus(UUID tenantId, UUID campusId, StudentStatus status);

    java.util.Optional<Student> findByTenantIdAndCnic(UUID tenantId, String cnic);

    Page<Student> findByTenantIdAndClassId(UUID tenantId, UUID classId, Pageable pageable);

    Page<Student> findByTenantIdAndClassIdIn(UUID tenantId, Collection<UUID> classIds, Pageable pageable);

    Page<Student> findByTenantIdAndCampusIdAndClassIdIn(UUID tenantId, UUID campusId, Collection<UUID> classIds, Pageable pageable);

    Page<Student> findByTenantIdAndSectionId(UUID tenantId, UUID sectionId, Pageable pageable);

    Page<Student> findByTenantIdAndClassIdAndSectionId(UUID tenantId, UUID classId, UUID sectionId, Pageable pageable);

    List<Student> findByTenantIdAndClassIdAndSectionIdAndStatus(
            UUID tenantId, UUID classId, UUID sectionId, StudentStatus status);

    List<Student> findByTenantIdAndClassIdAndStatus(UUID tenantId, UUID classId, StudentStatus status);

    List<Student> findByTenantIdAndClassIdInAndStatus(UUID tenantId, Collection<UUID> classIds, StudentStatus status);

    List<Student> findByTenantIdAndSectionIdAndStatus(UUID tenantId, UUID sectionId, StudentStatus status);

    List<Student> findByTenantIdAndSectionIdInAndStatus(UUID tenantId, Collection<UUID> sectionIds, StudentStatus status);

    List<Student> findByTenantIdAndClassId(UUID tenantId, UUID classId);

    Optional<Student> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    Optional<Student> findFirstByTenantIdAndRollNumberIgnoreCase(UUID tenantId, String rollNumber);

    List<Student> findByTenantId(UUID tenantId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, StudentStatus status);

    long countByTenantIdAndClassId(UUID tenantId, UUID classId);
}
