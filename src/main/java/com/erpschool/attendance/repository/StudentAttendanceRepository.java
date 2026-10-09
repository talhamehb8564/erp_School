package com.erpschool.attendance.repository;

import com.erpschool.attendance.entity.AttendanceStatus;
import com.erpschool.attendance.entity.StudentAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentAttendanceRepository extends JpaRepository<StudentAttendance, UUID> {

    interface CountRow {
        UUID getStudentId();
        AttendanceStatus getStatus();
        long getTotal();
    }
    List<StudentAttendance> findByTenantIdAndTimetableSlotIdAndAttendanceDate(UUID tenantId, UUID slotId, LocalDate date);
    List<StudentAttendance> findByTenantIdAndStudentIdAndAttendanceDateBetween(UUID tenantId, UUID studentId, LocalDate from, LocalDate to);
    long countByTenantIdAndStudentIdAndStatusAndAttendanceDateBetween(UUID tenantId, UUID studentId, AttendanceStatus status, LocalDate from, LocalDate to);
    long countByTenantIdAndStudentIdAndAttendanceDateBetween(UUID tenantId, UUID studentId, LocalDate from, LocalDate to);
    Optional<StudentAttendance> findByTenantIdAndTimetableSlotIdAndStudentIdAndAttendanceDate(
            UUID tenantId, UUID slotId, UUID studentId, LocalDate date);

    long countByTenantId(UUID tenantId);

    @Query("""
            select a.studentId as studentId, a.status as status, count(a.id) as total
            from StudentAttendance a
            where a.tenantId = :tenantId
              and a.studentId in :studentIds
              and a.attendanceDate between :from and :to
            group by a.studentId, a.status
            """)
    List<CountRow> countGrouped(
            @Param("tenantId") UUID tenantId,
            @Param("studentIds") Collection<UUID> studentIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
