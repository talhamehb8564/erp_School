package com.erpschool.exam.repository;

import com.erpschool.exam.entity.ExamSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ExamSessionRepository extends JpaRepository<ExamSession, UUID> {
    List<ExamSession> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<ExamSession> findByTenantIdAndPublishedTrueOrderByCreatedAtDesc(UUID tenantId);

    @Query("select s.id from ExamSession s where s.tenantId = :tenantId and s.published = true")
    List<UUID> findPublishedIds(@Param("tenantId") UUID tenantId);
}
