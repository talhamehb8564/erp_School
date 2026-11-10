package com.erpschool.attendance.repository;

import com.erpschool.attendance.entity.AttendanceStatus;
import com.erpschool.attendance.entity.TeacherAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherAttendanceRepository extends JpaRepository<TeacherAttendance, UUID> {

    interface StatusCount {
        AttendanceStatus getStatus();
        long getTotal();
    }
    Optional<TeacherAttendance> findByTenantIdAndTeacherUserIdAndAttendanceDate(UUID tenantId, UUID teacherUserId, LocalDate date);
    List<TeacherAttendance> findByTenantIdAndAttendanceDate(UUID tenantId, LocalDate date);
    List<TeacherAttendance> findByTenantIdAndAttendanceDateBetween(UUID tenantId, LocalDate from, LocalDate to);
    List<TeacherAttendance> findByTenantIdAndTeacherUserIdAndAttendanceDateBetween(UUID tenantId, UUID teacherUserId, LocalDate from, LocalDate to);

    long countByTenantIdAndTeacherUserIdAndAttendanceDateBetween(UUID tenantId, UUID teacherUserId, LocalDate from, LocalDate to);

    long countByTenantIdAndTeacherUserIdAndStatusAndAttendanceDateBetween(
            UUID tenantId, UUID teacherUserId, AttendanceStatus status, LocalDate from, LocalDate to);

    @Query("""
            select a.status as status, count(a.id) as total
            from TeacherAttendance a
            where a.tenantId = :tenantId
              and a.teacherUserId = :teacherUserId
              and a.attendanceDate between :from and :to
            group by a.status
            """)
    List<StatusCount> countGrouped(
            @Param("tenantId") UUID tenantId,
            @Param("teacherUserId") UUID teacherUserId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
