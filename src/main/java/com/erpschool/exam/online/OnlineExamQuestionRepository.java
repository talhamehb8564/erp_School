package com.erpschool.exam.online;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OnlineExamQuestionRepository extends JpaRepository<OnlineExamQuestion, UUID> {
    List<OnlineExamQuestion> findByTenantIdAndExamIdOrderBySortOrderAsc(UUID tenantId, UUID examId);
    List<OnlineExamQuestion> findByTenantIdAndExamIdIn(UUID tenantId, Collection<UUID> examIds);
    void deleteByTenantIdAndExamId(UUID tenantId, UUID examId);
}
