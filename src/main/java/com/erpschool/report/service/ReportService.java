package com.erpschool.report.service;

import com.erpschool.attendance.entity.AttendanceStatus;
import com.erpschool.attendance.repository.StudentAttendanceRepository;
import com.erpschool.attendance.repository.TeacherAttendanceRepository;
import com.erpschool.common.util.GradeCalculator;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.exam.entity.ExamResult;
import com.erpschool.exam.repository.ExamResultRepository;
import com.erpschool.exam.repository.ExamSessionRepository;
import com.erpschool.fee.entity.ChallanStatus;
import com.erpschool.fee.entity.FeeChallan;
import com.erpschool.fee.repository.FeeChallanRepository;
import com.erpschool.student.entity.ParentStudent;
import com.erpschool.student.entity.Student;
import com.erpschool.student.repository.ParentStudentRepository;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.student.service.StudentAccessService;
import com.erpschool.tenant.context.CampusScope;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final TeacherAttendanceRepository teacherAttendanceRepository;
    private final ExamSessionRepository examSessionRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final EntityManager entityManager;

    public ReportService(StudentRepository studentRepository,
                         UserRepository userRepository,
                         StudentAttendanceRepository studentAttendanceRepository,
                         FeeChallanRepository challanRepository,
                         ExamResultRepository examResultRepository,
                         StudentAccessService studentAccessService,
                         TenantRepository tenantRepository,
                         TeacherAttendanceRepository teacherAttendanceRepository,
                         ExamSessionRepository examSessionRepository,
                         ParentStudentRepository parentStudentRepository,
                         EntityManager entityManager) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.studentAttendanceRepository = studentAttendanceRepository;
        this.challanRepository = challanRepository;
        this.examResultRepository = examResultRepository;
        this.studentAccessService = studentAccessService;
        this.tenantRepository = tenantRepository;
        this.teacherAttendanceRepository = teacherAttendanceRepository;
        this.examSessionRepository = examSessionRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> schoolDashboard() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        boolean scoped = CampusScope.restricts() && CampusScope.current() != null;
        String campus = scoped ? " and campus_id = :campus" : "";
        String sql = """
                select
                  (select count(*) from students where tenant_id = :tid%s),
                  (select count(*) from students where tenant_id = :tid and status = 'ACTIVE'%s),
                  (select count(*) from users where tenant_id = :tid and role = 'TEACHER'%s),
                  (select count(*) from users where tenant_id = :tid and role = 'PARENT'%s),
                  (select count(*) from users where tenant_id = :tid and status = 'ACTIVE'%s),
                  (select count(*) from fee_challans where tenant_id = :tid and status = 'UNPAID'),
                  (select count(*) from fee_challans where tenant_id = :tid and status = 'PAYMENT_UNDER_VERIFICATION')
                """.formatted(campus, campus, campus, campus, campus);
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("tid", tenantId);
        if (scoped) {
            q.setParameter("campus", CampusScope.current());
        }
        Object[] cols = (Object[]) q.getSingleResult();
        Map<String, Object> m = new HashMap<>();
        m.put("students", num(cols[0]));
        m.put("activeStudents", num(cols[1]));
        m.put("teachers", num(cols[2]));
        m.put("parents", num(cols[3]));
        m.put("activeUsers", num(cols[4]));
        m.put("unpaidChallans", num(cols[5]));
        m.put("pendingFeeProofs", num(cols[6]));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studentProgress(UUID studentId) {
        studentAccessService.requireStudent(studentId);
        Map<String, Map<String, Object>> batch = studentProgressBatch(List.of(studentId));
        Map<String, Object> one = batch.get(studentId.toString());
        return one != null ? one : emptyProgress();
    }

    /**
     * One attendance group-query + one exam sum-query for every linked child.
     * Used by the parent portal so N children are not N+1 round-trips.
     */
    @Transactional(readOnly = true)
    public Map<String, Map<String, Object>> studentProgressBatch(Collection<UUID> studentIds) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        Map<String, Map<String, Object>> out = new HashMap<>();
        if (studentIds == null || studentIds.isEmpty()) {
            return out;
        }
        List<Student> students = studentRepository.findAllById(studentIds).stream()
                .filter(s -> tenantId.equals(s.getTenantId()))
                .toList();
        UserRole role = TenantContext.getRole();
        if (role == UserRole.PARENT) {
            Set<UUID> linked = parentStudentRepository
                    .findByTenantIdAndParentUserId(tenantId, TenantContext.getUserId())
                    .stream()
                    .map(ParentStudent::getStudentId)
                    .collect(Collectors.toSet());
            students = students.stream().filter(s -> linked.contains(s.getId())).toList();
        } else if (role == UserRole.STUDENT) {
            UUID me = TenantContext.getUserId();
            students = students.stream().filter(s -> me.equals(s.getUserId())).toList();
        }
        if (students.isEmpty()) {
            return out;
        }
        List<UUID> ids = students.stream().map(Student::getId).toList();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(30);
        Map<UUID, long[]> att = new HashMap<>();
        for (UUID id : ids) {
            att.put(id, new long[4]);
        }
        for (StudentAttendanceRepository.CountRow row : studentAttendanceRepository.countGrouped(tenantId, ids, from, to)) {
            long[] bucket = att.computeIfAbsent(row.getStudentId(), k -> new long[4]);
            bucket[0] += row.getTotal();
            if (row.getStatus() == AttendanceStatus.PRESENT) {
                bucket[1] += row.getTotal();
            } else if (row.getStatus() == AttendanceStatus.ABSENT) {
                bucket[2] += row.getTotal();
            } else if (row.getStatus() == AttendanceStatus.LATE) {
                bucket[3] += row.getTotal();
            }
        }
        Map<UUID, ExamResultRepository.MarkSum> marks = Map.of();
        List<UUID> published = examSessionRepository.findPublishedIds(tenantId);
        if (!published.isEmpty()) {
            marks = examResultRepository.sumPublishedMarks(tenantId, ids, published).stream()
                    .collect(Collectors.toMap(ExamResultRepository.MarkSum::getStudentId, r -> r, (a, b) -> a));
        }
        for (Student student : students) {
            long[] bucket = att.getOrDefault(student.getId(), new long[4]);
            long totalLectures = bucket[0];
            long present = bucket[1];
            ExamResultRepository.MarkSum sum = marks.get(student.getId());
            BigDecimal total = sum == null || sum.getTotalMarks() == null ? BigDecimal.ZERO : sum.getTotalMarks();
            BigDecimal obtained = sum == null || sum.getObtainedMarks() == null ? BigDecimal.ZERO : sum.getObtainedMarks();
            BigDecimal pct = GradeCalculator.percentage(obtained, total);
            Map<String, Object> m = new HashMap<>();
            m.put("attendancePercent", totalLectures == 0 ? 0 : Math.round(present * 10000.0 / totalLectures) / 100.0);
            m.put("present", present);
            m.put("absent", bucket[2]);
            m.put("late", bucket[3]);
            m.put("totalLectures", totalLectures);
            m.put("academicPercent", pct);
            m.put("grade", GradeCalculator.grade(pct));
            m.put("passStatus", GradeCalculator.passStatus(pct));
            m.put("obtainedMarks", obtained);
            m.put("totalMarks", total);
            out.put(student.getId().toString(), m);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> teacherProgress() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        UUID userId = TenantContext.getUserId();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(30);
        List<com.erpschool.attendance.entity.TeacherAttendance> rows =
                teacherAttendanceRepository.findByTenantIdAndTeacherUserIdAndAttendanceDateBetween(
                        tenantId, userId, from, to);
        long present = 0;
        long absent = 0;
        long late = 0;
        for (com.erpschool.attendance.entity.TeacherAttendance row : rows) {
            if (row.getStatus() == AttendanceStatus.PRESENT) {
                present++;
            } else if (row.getStatus() == AttendanceStatus.ABSENT) {
                absent++;
            } else if (row.getStatus() == AttendanceStatus.LATE) {
                late++;
            }
        }
        long total = rows.size();
        Map<String, Object> m = new HashMap<>();
        m.put("attendancePercent", total == 0 ? 0 : Math.round(present * 10000.0 / total) / 100.0);
        m.put("present", present);
        m.put("absent", absent);
        m.put("late", late);
        m.put("totalDays", total);
        m.put("academicPercent", 0);
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

    private static Map<String, Object> emptyProgress() {
        Map<String, Object> m = new HashMap<>();
        m.put("attendancePercent", 0);
        m.put("present", 0);
        m.put("absent", 0);
        m.put("late", 0);
        m.put("totalLectures", 0);
        m.put("academicPercent", 0);
        m.put("grade", GradeCalculator.grade(BigDecimal.ZERO));
        m.put("passStatus", GradeCalculator.passStatus(BigDecimal.ZERO));
        m.put("obtainedMarks", BigDecimal.ZERO);
        m.put("totalMarks", BigDecimal.ZERO);
        return m;
    }

    private static long num(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return 0;
    }
}
