package com.erpschool.exam.repository;

import com.erpschool.exam.entity.ExamSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExamSessionRepository extends JpaRepository<ExamSession, UUID> {
    List<ExamSession> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<ExamSession> findByTenantIdAndPublishedTrueOrderByCreatedAtDesc(UUID tenantId);
}
