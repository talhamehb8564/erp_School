package com.erpschool.exam.repository;

import com.erpschool.exam.entity.ExamDateSheetEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExamDateSheetRepository extends JpaRepository<ExamDateSheetEntry, UUID> {

    List<ExamDateSheetEntry> findByTenantIdAndExamSessionIdOrderByExamDateAscStartTimeAsc(
            UUID tenantId, UUID examSessionId);

    List<ExamDateSheetEntry> findByTenantIdAndExamSessionIdAndClassIdOrderByExamDateAscStartTimeAsc(
            UUID tenantId, UUID examSessionId, UUID classId);

    void deleteByTenantIdAndExamSessionId(UUID tenantId, UUID examSessionId);
}
