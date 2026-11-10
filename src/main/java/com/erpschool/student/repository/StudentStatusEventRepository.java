package com.erpschool.student.repository;

import com.erpschool.student.entity.StudentStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StudentStatusEventRepository extends JpaRepository<StudentStatusEvent, UUID> {

    List<StudentStatusEvent> findByTenantIdAndStudentIdOrderByCreatedAtDesc(UUID tenantId, UUID studentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into student_status_events (
                id, tenant_id, campus_id, student_id, from_status, to_status,
                reason_code, reason_note, actor_user_id, actor_username, bulk, created_at)
            select gen_random_uuid(), s.tenant_id, s.campus_id, s.id, s.status, :toStatus,
                   :reasonCode, :reasonNote, :actorId, :actorUsername, true, :now
            from students s
            where s.tenant_id = :tenantId
              and s.status = :fromStatus
              and s.class_id = :classId
              and (:hasSection = false or s.section_id = :sectionId)
              and (:hasCampus = false or s.campus_id = :campusId)
            """, nativeQuery = true)
    int insertBulkEvents(
            @Param("tenantId") UUID tenantId,
            @Param("classId") UUID classId,
            @Param("hasSection") boolean hasSection,
            @Param("sectionId") UUID sectionId,
            @Param("hasCampus") boolean hasCampus,
            @Param("campusId") UUID campusId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("reasonCode") String reasonCode,
            @Param("reasonNote") String reasonNote,
            @Param("actorId") UUID actorId,
            @Param("actorUsername") String actorUsername,
            @Param("now") Instant now);
}
