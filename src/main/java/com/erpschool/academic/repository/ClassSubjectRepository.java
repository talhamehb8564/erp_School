package com.erpschool.academic.repository;

import com.erpschool.academic.entity.ClassSubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClassSubjectRepository extends JpaRepository<ClassSubject, UUID> {
    List<ClassSubject> findByTenantIdAndClassId(UUID tenantId, UUID classId);
    boolean existsByTenantIdAndClassIdAndSubjectId(UUID tenantId, UUID classId, UUID subjectId);
}
