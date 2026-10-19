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
import com.erpschool.settings.entity.SchoolSettings;
import com.erpschool.settings.repository.SchoolSettingsRepository;
import com.erpschool.student.service.StudentAccessService;
import com.erpschool.tenant.context.CampusScope;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.entity.UserStatus;
import com.erpschool.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final SchoolSettingsRepository settingsRepository;
    private final UserRepository userRepository;

    public AttendanceService(StudentAttendanceRepository studentAttendanceRepository,
                             TeacherAttendanceRepository teacherAttendanceRepository,
                             SalaryDeductionRepository salaryDeductionRepository,
                             AcademicService academicService,
                             StudentRepository studentRepository,
                             StudentAccessService studentAccessService,
                             NotificationService notificationService,
                             SchoolSettingsRepository settingsRepository,
                             UserRepository userRepository) {
        this.studentAttendanceRepository = studentAttendanceRepository;
        this.teacherAttendanceRepository = teacherAttendanceRepository;
        this.salaryDeductionRepository = salaryDeductionRepository;
        this.academicService = academicService;
        this.studentRepository = studentRepository;
        this.studentAccessService = studentAccessService;
        this.notificationService = notificationService;
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
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

    @Transactional(readOnly = true)
    public List<StudentAttendance> myHistory(LocalDate from, LocalDate to) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        Student me = studentRepository.findByTenantIdAndUserId(tenantId, TenantContext.getUserId())
                .orElseThrow(() -> new BusinessException("No student profile is linked to this account"));
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(30);
        return studentAttendanceRepository.findByTenantIdAndStudentIdAndAttendanceDateBetween(
                tenantId, me.getId(), start, end);
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

        SchoolSettings settings = settingsRepository.findById(tenantId).orElse(null);
        boolean waive = settings != null && settings.isWaiveAttendanceDeduction();
        BigDecimal ruleAmount = BigDecimal.ZERO;
        if (settings != null) {
            ruleAmount = switch (status) {
                case ABSENT -> nvl(settings.getAbsentDeduction());
                case LATE -> nvl(settings.getLateDeduction());
                case LEAVE -> nvl(settings.getLeaveDeduction());
                default -> BigDecimal.ZERO;
            };
        }
        BigDecimal amount = deductionAmount != null ? deductionAmount : ruleAmount;
        boolean apply = !waive && status != AttendanceStatus.PRESENT && (deductSalary || amount.signum() > 0);
        if (apply) {
            SalaryDeduction d = new SalaryDeduction();
            d.setTenantId(tenantId);
            d.setTeacherUserId(teacherUserId);
            d.setTeacherAttendanceId(row.getId());
            d.setDeduct(true);
            d.setAmount(amount);
            d.setReason(status.name() + " on " + date);
            d.setMonth(date.withDayOfMonth(1));
            d.setCreatedBy(TenantContext.getUserId());
            salaryDeductionRepository.save(d);
            notificationService.notifyUser(tenantId, teacherUserId, "SALARY_DEDUCTION",
                    "Salary deduction", status.name() + " on " + date + " will be deducted from salary.",
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
        if (id == null) {
            return teacherAttendanceRepository.findByTenantIdAndAttendanceDateBetween(tenantId, from, to);
        }
        return teacherAttendanceRepository.findByTenantIdAndTeacherUserIdAndAttendanceDateBetween(
                tenantId, id, from, to);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> teacherDay(LocalDate date) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        List<User> teachers;
        if (CampusScope.restricts() && TenantContext.getCampusId() != null) {
            teachers = userRepository.findByTenantIdAndCampusIdAndRole(
                    tenantId, TenantContext.getCampusId(), UserRole.TEACHER);
        } else {
            teachers = userRepository.findByTenantIdAndRole(tenantId, UserRole.TEACHER);
        }
        Map<UUID, TeacherAttendance> today = new HashMap<>();
        for (TeacherAttendance row : teacherAttendanceRepository.findByTenantIdAndAttendanceDate(tenantId, date)) {
            today.put(row.getTeacherUserId(), row);
        }
        LocalDate from = date.minusDays(30);
        Map<UUID, int[]> stats = new HashMap<>();
        for (TeacherAttendance row : teacherAttendanceRepository.findByTenantIdAndAttendanceDateBetween(
                tenantId, from, date)) {
            int[] pair = stats.computeIfAbsent(row.getTeacherUserId(), k -> new int[2]);
            pair[1]++;
            if (row.getStatus() == AttendanceStatus.PRESENT) {
                pair[0]++;
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (User teacher : teachers) {
            if (teacher.getStatus() != UserStatus.ACTIVE) {
                continue;
            }
            TeacherAttendance mark = today.get(teacher.getId());
            int[] pair = stats.getOrDefault(teacher.getId(), new int[2]);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("teacherUserId", teacher.getId());
            row.put("fullName", teacher.getFullName());
            row.put("username", teacher.getUsername());
            row.put("campusId", teacher.getCampusId());
            row.put("status", mark == null ? null : mark.getStatus().name());
            row.put("remarks", mark == null ? null : mark.getRemarks());
            row.put("presentDays", pair[0]);
            row.put("markedDays", pair[1]);
            row.put("percent", pair[1] == 0 ? 0 : Math.round(pair[0] * 10000.0 / pair[1]) / 100.0);
            out.add(row);
        }
        return out;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static int isoDay(LocalDate date) {
        DayOfWeek d = date.getDayOfWeek();
        return d == DayOfWeek.SUNDAY ? 7 : d.getValue();
    }

    public record MarkItem(UUID studentId, AttendanceStatus status, String remarks) {
    }
}
