package com.erpschool.attendance.repository;

import com.erpschool.attendance.entity.AttendanceStatus;
import com.erpschool.attendance.entity.StudentAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentAttendanceRepository extends JpaRepository<StudentAttendance, UUID> {
    List<StudentAttendance> findByTenantIdAndTimetableSlotIdAndAttendanceDate(UUID tenantId, UUID slotId, LocalDate date);
    List<StudentAttendance> findByTenantIdAndStudentIdAndAttendanceDateBetween(UUID tenantId, UUID studentId, LocalDate from, LocalDate to);
    long countByTenantIdAndStudentIdAndStatusAndAttendanceDateBetween(UUID tenantId, UUID studentId, AttendanceStatus status, LocalDate from, LocalDate to);
    long countByTenantIdAndStudentIdAndAttendanceDateBetween(UUID tenantId, UUID studentId, LocalDate from, LocalDate to);
    Optional<StudentAttendance> findByTenantIdAndTimetableSlotIdAndStudentIdAndAttendanceDate(
            UUID tenantId, UUID slotId, UUID studentId, LocalDate date);

    long countByTenantId(UUID tenantId);
}
