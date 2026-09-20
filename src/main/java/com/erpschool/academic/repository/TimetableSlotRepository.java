package com.erpschool.academic.repository;

import com.erpschool.academic.entity.TimetableSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, UUID> {
    List<TimetableSlot> findByTenantIdAndClassIdAndSectionIdOrderByDayOfWeekAscStartTimeAsc(
            UUID tenantId, UUID classId, UUID sectionId);
    List<TimetableSlot> findByTenantIdAndTeacherUserIdOrderByDayOfWeekAscStartTimeAsc(
            UUID tenantId, UUID teacherUserId);
    List<TimetableSlot> findByTenantIdAndTeacherUserIdAndDayOfWeek(
            UUID tenantId, UUID teacherUserId, int dayOfWeek);
}
