package com.erpschool.exam.repository;

import com.erpschool.exam.entity.ExamDateSheetEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ExamDateSheetRepository extends JpaRepository<ExamDateSheetEntry, UUID> {

    List<ExamDateSheetEntry> findByTenantIdAndExamSessionIdOrderByExamDateAscStartTimeAsc(
            UUID tenantId, UUID examSessionId);

    List<ExamDateSheetEntry> findByTenantIdAndExamSessionIdAndClassIdOrderByExamDateAscStartTimeAsc(
            UUID tenantId, UUID examSessionId, UUID classId);

    List<ExamDateSheetEntry> findByTenantIdAndDateSheetIdOrderByExamDateAscStartTimeAsc(
            UUID tenantId, UUID dateSheetId);

    List<ExamDateSheetEntry> findByTenantIdAndDateSheetIdInOrderByExamDateAscStartTimeAsc(
            UUID tenantId, Collection<UUID> dateSheetIds);

    void deleteByTenantIdAndExamSessionId(UUID tenantId, UUID examSessionId);

    void deleteByTenantIdAndDateSheetId(UUID tenantId, UUID dateSheetId);
}
