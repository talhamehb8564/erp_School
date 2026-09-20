package com.erpschool.attendance.repository;

import com.erpschool.attendance.entity.TeacherAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherAttendanceRepository extends JpaRepository<TeacherAttendance, UUID> {
    Optional<TeacherAttendance> findByTenantIdAndTeacherUserIdAndAttendanceDate(UUID tenantId, UUID teacherUserId, LocalDate date);
    List<TeacherAttendance> findByTenantIdAndAttendanceDateBetween(UUID tenantId, LocalDate from, LocalDate to);
    List<TeacherAttendance> findByTenantIdAndTeacherUserIdAndAttendanceDateBetween(UUID tenantId, UUID teacherUserId, LocalDate from, LocalDate to);
}
