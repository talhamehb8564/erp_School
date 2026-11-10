package com.erpschool.exam.online;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OnlineExamSectionRepository extends JpaRepository<OnlineExamSection, OnlineExamSection.Key> {

    List<OnlineExamSection> findByTenantIdAndExamId(UUID tenantId, UUID examId);

    @Query("""
            SELECT DISTINCT e FROM OnlineExam e
            WHERE e.tenantId = :tenantId AND e.classId = :classId
              AND (e.sectionId = :sectionId
                   OR EXISTS (SELECT 1 FROM OnlineExamSection s
                              WHERE s.examId = e.id AND s.tenantId = :tenantId AND s.sectionId = :sectionId))
            ORDER BY e.startAt DESC
            """)
    List<OnlineExam> findAssigned(@Param("tenantId") UUID tenantId,
                                  @Param("classId") UUID classId,
                                  @Param("sectionId") UUID sectionId);

    List<OnlineExamSection> findByTenantIdAndExamIdIn(UUID tenantId, Collection<UUID> examIds);
}
