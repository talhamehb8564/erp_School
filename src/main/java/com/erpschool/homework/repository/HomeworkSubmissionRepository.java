package com.erpschool.homework.repository;

import com.erpschool.homework.entity.HomeworkSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeworkSubmissionRepository extends JpaRepository<HomeworkSubmission, UUID> {

    List<HomeworkSubmission> findByTenantIdAndHomeworkIdOrderBySubmittedAtDesc(UUID tenantId, UUID homeworkId);

    Optional<HomeworkSubmission> findByTenantIdAndHomeworkIdAndStudentId(UUID tenantId, UUID homeworkId, UUID studentId);

    List<HomeworkSubmission> findByTenantIdAndStudentIdOrderBySubmittedAtDesc(UUID tenantId, UUID studentId);
}
