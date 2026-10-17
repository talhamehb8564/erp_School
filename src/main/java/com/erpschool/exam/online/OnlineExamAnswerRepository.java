package com.erpschool.exam.online;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnlineExamAnswerRepository extends JpaRepository<OnlineExamAnswer, UUID> {
    List<OnlineExamAnswer> findByTenantIdAndAttemptId(UUID tenantId, UUID attemptId);
    List<OnlineExamAnswer> findByTenantIdAndAttemptIdIn(UUID tenantId, Collection<UUID> attemptIds);
    Optional<OnlineExamAnswer> findByTenantIdAndAttemptIdAndQuestionId(UUID tenantId, UUID attemptId, UUID questionId);
}
