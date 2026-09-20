package com.erpschool.report.service;

import com.erpschool.attendance.entity.AttendanceStatus;
import com.erpschool.attendance.repository.StudentAttendanceRepository;
import com.erpschool.common.util.GradeCalculator;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.exam.entity.ExamResult;
import com.erpschool.exam.repository.ExamResultRepository;
import com.erpschool.fee.entity.ChallanStatus;
import com.erpschool.fee.entity.FeeChallan;
import com.erpschool.fee.repository.FeeChallanRepository;
import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.student.service.StudentAccessService;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.entity.UserStatus;
import com.erpschool.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final StudentAttendanceRepository studentAttendanceRepository;
    private final FeeChallanRepository challanRepository;
    private final ExamResultRepository examResultRepository;
    private final StudentAccessService studentAccessService;
    private final TenantRepository tenantRepository;

    public ReportService(StudentRepository studentRepository,
                         UserRepository userRepository,
                         StudentAttendanceRepository studentAttendanceRepository,
                         FeeChallanRepository challanRepository,
                         ExamResultRepository examResultRepository,
                         StudentAccessService studentAccessService,
                         TenantRepository tenantRepository) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.studentAttendanceRepository = studentAttendanceRepository;
        this.challanRepository = challanRepository;
        this.examResultRepository = examResultRepository;
        this.studentAccessService = studentAccessService;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> schoolDashboard() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        Map<String, Object> m = new HashMap<>();
        m.put("students", studentRepository.countByTenantId(tenantId));
        m.put("activeStudents", studentRepository.countByTenantIdAndStatus(tenantId, StudentStatus.ACTIVE));
        m.put("teachers", userRepository.countByTenantIdAndRole(tenantId, UserRole.TEACHER));
        m.put("parents", userRepository.countByTenantIdAndRole(tenantId, UserRole.PARENT));
        m.put("activeUsers", userRepository.countByTenantIdAndStatus(tenantId, UserStatus.ACTIVE));
        m.put("unpaidChallans", challanRepository.countByTenantIdAndStatus(tenantId, ChallanStatus.UNPAID));
        m.put("pendingFeeProofs", challanRepository.countByTenantIdAndStatus(tenantId, ChallanStatus.PAYMENT_UNDER_VERIFICATION));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> platformDashboard() {
        Map<String, Object> m = new HashMap<>();
        m.put("schools", tenantRepository.count());
        m.put("users", userRepository.count());
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studentAttendance(UUID studentId, LocalDate from, LocalDate to) {
        Student student = studentAccessService.requireStudent(studentId);
        long total = studentAttendanceRepository.countByTenantIdAndStudentIdAndAttendanceDateBetween(
                student.getTenantId(), studentId, from, to);
        long present = studentAttendanceRepository.countByTenantIdAndStudentIdAndStatusAndAttendanceDateBetween(
                student.getTenantId(), studentId, AttendanceStatus.PRESENT, from, to);
        long absent = studentAttendanceRepository.countByTenantIdAndStudentIdAndStatusAndAttendanceDateBetween(
                student.getTenantId(), studentId, AttendanceStatus.ABSENT, from, to);
        long late = studentAttendanceRepository.countByTenantIdAndStudentIdAndStatusAndAttendanceDateBetween(
                student.getTenantId(), studentId, AttendanceStatus.LATE, from, to);
        Map<String, Object> m = new HashMap<>();
        m.put("studentId", studentId);
        m.put("from", from);
        m.put("to", to);
        m.put("totalLectures", total);
        m.put("present", present);
        m.put("absent", absent);
        m.put("late", late);
        m.put("percentage", total == 0 ? 0 : Math.round(present * 10000.0 / total) / 100.0);
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> feeCollection(LocalDate month) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        LocalDate monthStart = month.withDayOfMonth(1);
        List<FeeChallan> rows = challanRepository.findByTenantIdAndMonth(tenantId, monthStart);
        BigDecimal billed = rows.stream().map(FeeChallan::getTotalPayable).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = rows.stream()
                .filter(c -> c.getStatus() == ChallanStatus.PAID)
                .map(FeeChallan::getTotalPayable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = new HashMap<>();
        m.put("month", monthStart);
        m.put("challanCount", rows.size());
        m.put("billed", billed);
        m.put("collected", collected);
        m.put("outstanding", billed.subtract(collected));
        m.put("paidCount", rows.stream().filter(c -> c.getStatus() == ChallanStatus.PAID).count());
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> examClass(UUID sessionId, UUID classId) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        List<Student> students = studentRepository.findByTenantIdAndClassId(tenantId, classId);
        Map<UUID, List<ExamResult>> byStudent = examResultRepository
                .findByTenantIdAndExamSessionId(tenantId, sessionId)
                .stream()
                .collect(Collectors.groupingBy(ExamResult::getStudentId));
        return students.stream().map(s -> {
            List<ExamResult> results = byStudent.getOrDefault(s.getId(), List.of());
            BigDecimal total = results.stream().map(ExamResult::getTotalMarks).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal obtained = results.stream().map(ExamResult::getObtainedMarks).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pct = GradeCalculator.percentage(obtained, total);
            Map<String, Object> row = new HashMap<>();
            row.put("studentId", s.getId());
            row.put("admissionNumber", s.getAdmissionNumber());
            row.put("obtainedMarks", obtained);
            row.put("totalMarks", total);
            row.put("percentage", pct);
            row.put("grade", GradeCalculator.grade(pct));
            row.put("passStatus", GradeCalculator.passStatus(pct));
            return row;
        }).toList();
    }
}
