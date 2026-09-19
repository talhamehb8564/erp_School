package com.erpschool.attendance.service;

import com.erpschool.academic.entity.TimetableSlot;
import com.erpschool.academic.service.AcademicService;
import com.erpschool.attendance.entity.AttendanceStatus;
import com.erpschool.attendance.entity.SalaryDeduction;
import com.erpschool.attendance.entity.StudentAttendance;
import com.erpschool.attendance.entity.TeacherAttendance;
import com.erpschool.attendance.repository.SalaryDeductionRepository;
import com.erpschool.attendance.repository.StudentAttendanceRepository;
import com.erpschool.attendance.repository.TeacherAttendanceRepository;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.notification.service.NotificationService;
import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.student.service.StudentAccessService;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.user.entity.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AttendanceService {

    private final StudentAttendanceRepository studentAttendanceRepository;
    private final TeacherAttendanceRepository teacherAttendanceRepository;
    private final SalaryDeductionRepository salaryDeductionRepository;
    private final AcademicService academicService;
    private final StudentRepository studentRepository;
    private final StudentAccessService studentAccessService;
    private final NotificationService notificationService;

    public AttendanceService(StudentAttendanceRepository studentAttendanceRepository,
                             TeacherAttendanceRepository teacherAttendanceRepository,
                             SalaryDeductionRepository salaryDeductionRepository,
                             AcademicService academicService,
                             StudentRepository studentRepository,
                             StudentAccessService studentAccessService,
                             NotificationService notificationService) {
        this.studentAttendanceRepository = studentAttendanceRepository;
        this.teacherAttendanceRepository = teacherAttendanceRepository;
        this.salaryDeductionRepository = salaryDeductionRepository;
        this.academicService = academicService;
        this.studentRepository = studentRepository;
        this.studentAccessService = studentAccessService;
        this.notificationService = notificationService;
    }

    @Transactional
    public List<StudentAttendance> markLecture(UUID slotId, LocalDate date, List<MarkItem> marks) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        TimetableSlot slot = academicService.requireSlot(slotId);
        if (isoDay(date) != slot.getDayOfWeek()) {
            throw new BusinessException("Attendance date does not match this lecture's weekday");
        }
        UserRole role = TenantContext.getRole();
        if (role == UserRole.TEACHER) {
            if (!slot.getTeacherUserId().equals(TenantContext.getUserId())) {
                throw new ForbiddenException("Teachers can only mark attendance for their own lectures");
            }
        }
        List<StudentAttendance> saved = new ArrayList<>();
        for (MarkItem item : marks) {
            Student student = studentAccessService.requireStudent(item.studentId());
            if (!slot.getClassId().equals(student.getClassId()) || !slot.getSectionId().equals(student.getSectionId())) {
                throw new BusinessException("Student is not in this lecture's class/section");
            }
            StudentAttendance row = studentAttendanceRepository
                    .findByTenantIdAndTimetableSlotIdAndStudentIdAndAttendanceDate(
                            tenantId, slotId, item.studentId(), date)
                    .orElseGet(StudentAttendance::new);
            row.setTenantId(tenantId);
            row.setTimetableSlotId(slotId);
            row.setStudentId(item.studentId());
            row.setTeacherUserId(slot.getTeacherUserId());
            row.setAttendanceDate(date);
            row.setStatus(item.status());
            row.setRemarks(item.remarks());
            if (row.getId() == null) {
                row.setCreatedBy(TenantContext.getUserId());
            } else {
                row.setUpdatedBy(TenantContext.getUserId());
            }
            saved.add(studentAttendanceRepository.save(row));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<StudentAttendance> lectureSheet(UUID slotId, LocalDate date) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        academicService.requireSlot(slotId);
        return studentAttendanceRepository.findByTenantIdAndTimetableSlotIdAndAttendanceDate(tenantId, slotId, date);
    }

    @Transactional(readOnly = true)
    public List<Student> lectureRoster(UUID slotId) {
        TimetableSlot slot = academicService.requireSlot(slotId);
        return studentRepository.findByTenantIdAndClassIdAndSectionIdAndStatus(
                slot.getTenantId(), slot.getClassId(), slot.getSectionId(), StudentStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<StudentAttendance> studentHistory(UUID studentId, LocalDate from, LocalDate to) {
        Student student = studentAccessService.requireStudent(studentId);
        return studentAttendanceRepository.findByTenantIdAndStudentIdAndAttendanceDateBetween(
                student.getTenantId(), studentId, from, to);
    }

    @Transactional
    public TeacherAttendance markTeacher(UUID teacherUserId, LocalDate date, AttendanceStatus status,
                                         String remarks, boolean deductSalary, BigDecimal deductionAmount) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        TeacherAttendance row = teacherAttendanceRepository
                .findByTenantIdAndTeacherUserIdAndAttendanceDate(tenantId, teacherUserId, date)
                .orElseGet(TeacherAttendance::new);
        row.setTenantId(tenantId);
        row.setTeacherUserId(teacherUserId);
        row.setAttendanceDate(date);
        row.setStatus(status);
        row.setRemarks(remarks);
        if (row.getId() == null) {
            row.setCreatedBy(TenantContext.getUserId());
        } else {
            row.setUpdatedBy(TenantContext.getUserId());
        }
        row = teacherAttendanceRepository.save(row);

        if (deductSalary && status == AttendanceStatus.ABSENT) {
            SalaryDeduction d = new SalaryDeduction();
            d.setTenantId(tenantId);
            d.setTeacherUserId(teacherUserId);
            d.setTeacherAttendanceId(row.getId());
            d.setDeduct(true);
            d.setAmount(deductionAmount == null ? BigDecimal.ZERO : deductionAmount);
            d.setReason("Absent on " + date);
            d.setMonth(date.withDayOfMonth(1));
            d.setCreatedBy(TenantContext.getUserId());
            salaryDeductionRepository.save(d);
            notificationService.notifyUser(tenantId, teacherUserId, "SALARY_DEDUCTION",
                    "Salary deduction", "Absence on " + date + " will be deducted from salary.",
                    "TeacherAttendance", row.getId().toString());
        }
        return row;
    }

    @Transactional(readOnly = true)
    public List<TeacherAttendance> teacherHistory(UUID teacherUserId, LocalDate from, LocalDate to) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        UUID id = teacherUserId;
        if (TenantContext.getRole() == UserRole.TEACHER) {
            id = TenantContext.getUserId();
        }
        return teacherAttendanceRepository.findByTenantIdAndTeacherUserIdAndAttendanceDateBetween(
                tenantId, id, from, to);
    }

    public static int isoDay(LocalDate date) {
        DayOfWeek d = date.getDayOfWeek();
        return d == DayOfWeek.SUNDAY ? 7 : d.getValue();
    }

    public record MarkItem(UUID studentId, AttendanceStatus status, String remarks) {
    }
}
