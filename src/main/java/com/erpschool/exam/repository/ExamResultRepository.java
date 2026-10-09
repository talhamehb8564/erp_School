package com.erpschool.exam.repository;

import com.erpschool.exam.entity.ExamResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamResultRepository extends JpaRepository<ExamResult, UUID> {
    List<ExamResult> findByTenantIdAndExamSessionIdAndStudentId(UUID tenantId, UUID examSessionId, UUID studentId);
    List<ExamResult> findByTenantIdAndStudentId(UUID tenantId, UUID studentId);
    List<ExamResult> findByTenantIdAndExamSessionId(UUID tenantId, UUID examSessionId);
    Optional<ExamResult> findByTenantIdAndExamSessionIdAndStudentIdAndSubjectId(
            UUID tenantId, UUID examSessionId, UUID studentId, UUID subjectId);
}
