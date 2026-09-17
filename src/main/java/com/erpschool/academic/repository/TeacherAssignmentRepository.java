package com.erpschool.academic.repository;

import com.erpschool.academic.entity.TeacherAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, UUID> {
    List<TeacherAssignment> findByTenantIdAndTeacherUserId(UUID tenantId, UUID teacherUserId);
    List<TeacherAssignment> findByTenantId(UUID tenantId);
    boolean existsByTenantIdAndTeacherUserIdAndClassIdAndSectionIdAndSubjectId(
            UUID tenantId, UUID teacherUserId, UUID classId, UUID sectionId, UUID subjectId);
}
