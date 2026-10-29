package com.erpschool.exam.repository;

import com.erpschool.exam.entity.ExamDateSheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamDateSheetHeaderRepository extends JpaRepository<ExamDateSheet, UUID> {

    List<ExamDateSheet> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

    Optional<ExamDateSheet> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ExamDateSheet> findByTenantIdAndExamSessionIdAndClassId(UUID tenantId, UUID examSessionId, UUID classId);

    void deleteByTenantIdAndExamSessionId(UUID tenantId, UUID examSessionId);
}
