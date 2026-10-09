package com.erpschool.exam.repository;

import com.erpschool.exam.entity.ExamResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamResultRepository extends JpaRepository<ExamResult, UUID> {

    interface MarkSum {
        UUID getStudentId();
        BigDecimal getTotalMarks();
        BigDecimal getObtainedMarks();
    }
    List<ExamResult> findByTenantIdAndExamSessionIdAndStudentId(UUID tenantId, UUID examSessionId, UUID studentId);
    List<ExamResult> findByTenantIdAndStudentId(UUID tenantId, UUID studentId);
    List<ExamResult> findByTenantIdAndExamSessionId(UUID tenantId, UUID examSessionId);
    Optional<ExamResult> findByTenantIdAndExamSessionIdAndStudentIdAndSubjectId(
            UUID tenantId, UUID examSessionId, UUID studentId, UUID subjectId);

    @Query("""
            select r.studentId as studentId, sum(r.totalMarks) as totalMarks, sum(r.obtainedMarks) as obtainedMarks
            from ExamResult r
            where r.tenantId = :tenantId
              and r.studentId in :studentIds
              and r.examSessionId in :sessionIds
            group by r.studentId
            """)
    List<MarkSum> sumPublishedMarks(
            @Param("tenantId") UUID tenantId,
            @Param("studentIds") Collection<UUID> studentIds,
            @Param("sessionIds") Collection<UUID> sessionIds);
}
