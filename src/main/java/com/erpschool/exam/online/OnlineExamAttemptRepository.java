package com.erpschool.exam.online;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnlineExamAttemptRepository extends JpaRepository<OnlineExamAttempt, UUID> {
    Optional<OnlineExamAttempt> findByTenantIdAndExamIdAndStudentId(UUID tenantId, UUID examId, UUID studentId);
    List<OnlineExamAttempt> findByTenantIdAndExamIdOrderBySubmittedAtDesc(UUID tenantId, UUID examId);
    List<OnlineExamAttempt> findByTenantIdAndStudentIdOrderByStartedAtDesc(UUID tenantId, UUID studentId);
    boolean existsByTenantIdAndExamId(UUID tenantId, UUID examId);
    long countByTenantIdAndExamId(UUID tenantId, UUID examId);
}
