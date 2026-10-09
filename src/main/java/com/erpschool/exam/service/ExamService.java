package com.erpschool.exam.service;

import com.erpschool.academic.entity.ClassSubject;
import com.erpschool.academic.entity.TeacherAssignment;
import com.erpschool.academic.repository.ClassSubjectRepository;
import com.erpschool.academic.repository.TeacherAssignmentRepository;
import com.erpschool.academic.service.AcademicService;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.GradeCalculator;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.exam.entity.ExamDateSheetEntry;
import com.erpschool.exam.entity.ExamResult;
import com.erpschool.exam.entity.ExamSession;
import com.erpschool.exam.repository.ExamDateSheetRepository;
import com.erpschool.exam.repository.ExamResultRepository;
import com.erpschool.exam.repository.ExamSessionRepository;
import com.erpschool.notification.service.NotificationService;
import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import com.erpschool.student.repository.ParentStudentRepository;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.student.service.StudentAccessService;
import com.erpschool.tenant.context.CampusScope;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExamService {

    private final ExamSessionRepository sessionRepository;
    private final ExamResultRepository resultRepository;
    private final ExamDateSheetRepository dateSheetRepository;
    private final AcademicService academicService;
    private final StudentAccessService studentAccessService;
    private final StudentRepository studentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final TeacherAssignmentRepository assignmentRepository;

    public ExamService(
            ExamSessionRepository sessionRepository,
            ExamResultRepository resultRepository,
            ExamDateSheetRepository dateSheetRepository,
            AcademicService academicService,
            StudentAccessService studentAccessService,
            StudentRepository studentRepository,
            ParentStudentRepository parentStudentRepository,
            NotificationService notificationService,
            UserRepository userRepository,
            ClassSubjectRepository classSubjectRepository,
            TeacherAssignmentRepository assignmentRepository) {

        this.sessionRepository = sessionRepository;
        this.resultRepository = resultRepository;
        this.dateSheetRepository = dateSheetRepository;
        this.academicService = academicService;
        this.studentAccessService = studentAccessService;
        this.studentRepository = studentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.classSubjectRepository = classSubjectRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional
    public ExamSession createSession(ExamSession session) {

        UUID tenantId = TenantGuard.requireTenantId(null);
        session.setTenantId(tenantId);
        if (session.getCampusId() == null && CampusScope.restricts()) {
            session.setCampusId(CampusScope.current());
        }
        session.setPublished(false);
        session.setCreatedBy(TenantContext.getUserId());
        ExamSession saved = sessionRepository.save(session);
        notifyStaffOfExam(saved);
        return saved;
    }

    @Transactional
    public ExamSession setAnnounceAt(UUID sessionId, Instant announceAt) {
        ExamSession session = requireSession(sessionId);
        session.setAnnounceAt(announceAt);
        session.setUpdatedBy(TenantContext.getUserId());
        return sessionRepository.save(session);
    }

    @Transactional
    public List<ExamDateSheetEntry> replaceDateSheet(UUID sessionId, List<ExamDateSheetEntry> entries) {
        ExamSession session = requireSession(sessionId);
        UUID tenantId = session.getTenantId();
        dateSheetRepository.deleteByTenantIdAndExamSessionId(tenantId, sessionId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<ExamDateSheetEntry> saved = new java.util.ArrayList<>();
        for (ExamDateSheetEntry incoming : entries) {
            academicService.requireClass(incoming.getClassId());
            academicService.requireSubject(incoming.getSubjectId());
            if (incoming.getSectionId() != null) {
                academicService.requireSection(incoming.getSectionId());
            }
            ExamDateSheetEntry row = new ExamDateSheetEntry();
            row.setTenantId(tenantId);
            row.setExamSessionId(sessionId);
            row.setCampusId(incoming.getCampusId());
            row.setClassId(incoming.getClassId());
            row.setSectionId(incoming.getSectionId());
            row.setSubjectId(incoming.getSubjectId());
            row.setExamDate(incoming.getExamDate());
            row.setStartTime(incoming.getStartTime());
            row.setEndTime(incoming.getEndTime());
            row.setCreatedBy(TenantContext.getUserId());
            saved.add(dateSheetRepository.save(row));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ExamDateSheetEntry> dateSheet(UUID sessionId, UUID classId) {
        ExamSession session = requireSession(sessionId);
        if (classId != null) {
            return dateSheetRepository.findByTenantIdAndExamSessionIdAndClassIdOrderByExamDateAscStartTimeAsc(
                    session.getTenantId(), sessionId, classId);
        }
        return dateSheetRepository.findByTenantIdAndExamSessionIdOrderByExamDateAscStartTimeAsc(
                session.getTenantId(), sessionId);
    }

    @Transactional(readOnly = true)
    public List<ExamSession> sessions() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        List<ExamSession> rows = sessionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        if (CampusScope.restricts()) {
            UUID campusId = CampusScope.current();
            rows = rows.stream()
                    .filter(s -> s.getCampusId() == null || campusId.equals(s.getCampusId()))
                    .toList();
        }
        return rows;
    }

    @Transactional
    public ExamResult upsertResult(
            UUID sessionId,
            UUID studentId,
            UUID subjectId,
            BigDecimal total,
            BigDecimal obtained,
            String remarks,
            boolean absent) {

        UUID tenantId = TenantGuard.requireTenantId(null);

        ExamSession session = requireSession(sessionId);

        if (session.isPublished()) {
            throw new BusinessException(
                    "Cannot change results after they are published"
            );
        }

        Student student = studentAccessService.requireStudent(studentId);

        academicService.requireSubject(subjectId);
        assertTeacherMayEnter(tenantId, student, subjectId);

        if (absent) {
            obtained = BigDecimal.ZERO;
            if (remarks == null || remarks.isBlank()) {
                remarks = "ABSENT";
            }
        }

        if (obtained.compareTo(total) > 0) {
            throw new BusinessException(
                    "Obtained marks cannot exceed total marks"
            );
        }

        BigDecimal pct =
                GradeCalculator.percentage(
                        obtained,
                        total
                );

        ExamResult row = resultRepository
                .findByTenantIdAndExamSessionIdAndStudentIdAndSubjectId(
                        tenantId,
                        sessionId,
                        studentId,
                        subjectId
                )
                .orElseGet(ExamResult::new);

        row.setTenantId(tenantId);
        row.setExamSessionId(sessionId);
        row.setStudentId(studentId);
        row.setSubjectId(subjectId);
        row.setTotalMarks(total);
        row.setObtainedMarks(obtained);
        row.setPercentage(pct);
        row.setGrade(
                GradeCalculator.grade(pct)
        );
        row.setPassStatus(
                GradeCalculator.passStatus(pct)
        );
        row.setRemarks(remarks);
        row.setAbsent(absent);

        if (row.getId() == null) {
            row.setCreatedBy(
                    TenantContext.getUserId()
            );
        } else {
            row.setUpdatedBy(
                    TenantContext.getUserId()
            );
        }

        return resultRepository.save(row);
    }

    @Transactional
    public ExamSession publish(UUID sessionId) {

        ExamSession session = requireSession(sessionId);

        session.setPublished(true);

        session.setPublishedAt(
                Instant.now()
        );

        session.setUpdatedBy(
                TenantContext.getUserId()
        );

        session = sessionRepository.save(session);

        /*
         * IMPORTANT:
         *
         * 'session' is reassigned above, so Java does not consider
         * it effectively final. We therefore copy the required
         * values into final local variables before using them
         * inside the lambda below.
         */
        final UUID tenantId = session.getTenantId();
        final UUID examSessionId = session.getId();
        final String sessionName = session.getName();

        resultRepository
                .findByTenantIdAndExamSessionId(
                        tenantId,
                        sessionId
                )
                .forEach(result -> {

                    Student student =
                            studentRepository
                                    .findById(result.getStudentId())
                                    .orElse(null);

                    if (student != null) {

                        notificationService.notifyUser(
                                tenantId,
                                student.getUserId(),
                                "RESULT",
                                "Result published: "
                                        + sessionName,
                                "Grade "
                                        + result.getGrade()
                                        + " ("
                                        + result.getPercentage()
                                        + "%)",
                                "ExamSession",
                                examSessionId.toString()
                        );

                        parentStudentRepository
                                .findByTenantIdAndStudentId(
                                        tenantId,
                                        student.getId()
                                )
                                .forEach(link ->
                                        notificationService.notifyUser(
                                                tenantId,
                                                link.getParentUserId(),
                                                "RESULT",
                                                "Result published: "
                                                        + sessionName,
                                                "Your child's result is available.",
                                                "ExamSession",
                                                examSessionId.toString()
                                        )
                                );
                    }
                });

        return session;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studentResult(
            UUID sessionId,
            UUID studentId) {

        ExamSession session =
                requireSession(sessionId);

        Student student =
                studentAccessService.requireStudent(studentId);

        UserRole role =
                TenantContext.getRole();

        if (role == UserRole.PARENT || role == UserRole.STUDENT) {
            Instant announceAt = session.getAnnounceAt();
            if (announceAt != null && Instant.now().isBefore(announceAt)) {
                Map<String, Object> waiting = new HashMap<>();
                waiting.put("announced", false);
                waiting.put("pending", false);
                waiting.put("announceAt", announceAt);
                waiting.put("countdownSeconds", Math.max(0, ChronoUnit.SECONDS.between(Instant.now(), announceAt)));
                waiting.put("session", session);
                waiting.put("studentId", student.getId());
                waiting.put("rollNumber", student.getRollNumber());
                waiting.put("subjects", List.of());
                return waiting;
            }
            boolean timeReached = announceAt == null || !Instant.now().isBefore(announceAt);
            if (!session.isPublished() && (announceAt == null || !timeReached)) {
                throw new ForbiddenException("Results are not published yet");
            }
            if (!marksComplete(session, student)) {
                Map<String, Object> pending = new HashMap<>();
                pending.put("announced", true);
                pending.put("pending", true);
                pending.put("announceAt", announceAt);
                pending.put("session", session);
                pending.put("studentId", student.getId());
                pending.put("rollNumber", student.getRollNumber());
                pending.put("subjects", List.of());
                pending.put("message", "Result Pending - Awaiting Subject Submission");
                return pending;
            }
        }

        List<ExamResult> rows =
                resultRepository
                        .findByTenantIdAndExamSessionIdAndStudentId(
                                session.getTenantId(),
                                sessionId,
                                student.getId()
                        );

        BigDecimal total =
                rows.stream()
                        .map(ExamResult::getTotalMarks)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal obtained =
                rows.stream()
                        .map(ExamResult::getObtainedMarks)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal pct =
                GradeCalculator.percentage(
                        obtained,
                        total
                );

        Map<String, Object> m =
                new HashMap<>();

        m.put("announced", true);
        m.put("pending", false);
        m.put("announceAt", session.getAnnounceAt());
        m.put("session", session);
        m.put("studentId", student.getId());
        m.put("rollNumber", student.getRollNumber());
        m.put("admissionNumber", student.getAdmissionNumber());
        m.put("classId", student.getClassId());
        m.put("sectionId", student.getSectionId());
        if (student.getUserId() != null) {
            userRepository.findById(student.getUserId()).map(User::getFullName).ifPresent(n -> m.put("studentName", n));
        }
        m.put("subjects", rows);
        m.put("totalMarks", total);
        m.put("obtainedMarks", obtained);
        m.put("percentage", pct);
        m.put(
                "grade",
                GradeCalculator.grade(pct)
        );
        m.put(
                "passStatus",
                GradeCalculator.passStatus(pct)
        );

        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studentResultByRoll(UUID sessionId, String rollNumber) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        if (rollNumber == null || rollNumber.isBlank()) {
            throw new BusinessException("Enter a roll number");
        }
        String roll = rollNumber.trim();
        Student student = studentRepository.findFirstByTenantIdAndRollNumberIgnoreCase(tenantId, roll)
                .orElseThrow(() -> new ResourceNotFoundException("No result found for roll number " + roll));
        try {
            Map<String, Object> summary = studentResult(sessionId, student.getId());
            if (Boolean.TRUE.equals(summary.get("pending"))) {
                return summary;
            }
            List<?> subjects = (List<?>) summary.get("subjects");
            if (subjects == null || subjects.isEmpty()) {
                throw new ResourceNotFoundException("No result found for roll number " + roll);
            }
            return summary;
        } catch (ForbiddenException ex) {
            throw new ResourceNotFoundException("No result found for roll number " + roll);
        }
    }

    @Transactional(readOnly = true)
    public List<ExamResult> sessionResults(
            UUID sessionId) {

        ExamSession session =
                requireSession(sessionId);

        return resultRepository
                .findByTenantIdAndExamSessionId(
                        session.getTenantId(),
                        sessionId
                );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> entryStatus(UUID sessionId) {
        ExamSession session = requireSession(sessionId);
        UUID tenantId = session.getTenantId();
        List<TeacherAssignment> assignments = assignmentRepository.findByTenantId(tenantId);
        if (CampusScope.restricts()) {
            java.util.Set<UUID> classIds = academicService.classes().stream()
                    .map(c -> c.getId()).collect(java.util.stream.Collectors.toSet());
            assignments = assignments.stream().filter(a -> classIds.contains(a.getClassId())).toList();
        }
        List<ExamResult> results = resultRepository.findByTenantIdAndExamSessionId(tenantId, sessionId);
        java.util.Set<String> entered = new java.util.HashSet<>();
        for (ExamResult r : results) {
            entered.add(r.getStudentId() + ":" + r.getSubjectId());
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (TeacherAssignment a : assignments) {
            List<Student> roster = studentRepository.findByTenantIdAndClassIdAndSectionIdAndStatus(
                    tenantId, a.getClassId(), a.getSectionId(), StudentStatus.ACTIVE);
            int expected = roster.size();
            int done = 0;
            for (Student st : roster) {
                if (entered.contains(st.getId() + ":" + a.getSubjectId())) {
                    done++;
                }
            }
            Map<String, Object> row = new HashMap<>();
            row.put("teacherUserId", a.getTeacherUserId());
            row.put("classId", a.getClassId());
            row.put("sectionId", a.getSectionId());
            row.put("subjectId", a.getSubjectId());
            row.put("expected", expected);
            row.put("entered", done);
            row.put("complete", expected > 0 && done >= expected);
            out.add(row);
        }
        return out;
    }

    private void notifyStaffOfExam(ExamSession session) {
        UUID tenantId = session.getTenantId();
        List<User> teachers = CampusScope.restricts()
                ? userRepository.findByTenantIdAndCampusIdAndRole(tenantId, CampusScope.current(), UserRole.TEACHER)
                : userRepository.findByTenantIdAndRole(tenantId, UserRole.TEACHER);
        for (User teacher : teachers) {
            notificationService.notifyUser(tenantId, teacher.getId(), "EXAM",
                    "Marks entry: " + session.getName(),
                    "Enter marks for your assigned subjects before the announcement.",
                    "ExamSession", session.getId().toString());
        }
        List<User> admins = CampusScope.restricts()
                ? userRepository.findByTenantIdAndCampusIdAndRole(tenantId, CampusScope.current(), UserRole.SCHOOL_ADMIN)
                : userRepository.findByTenantIdAndRole(tenantId, UserRole.SCHOOL_ADMIN);
        for (User admin : admins) {
            notificationService.notifyUser(tenantId, admin.getId(), "EXAM",
                    "Exam created: " + session.getName(),
                    "Teachers can now enter marks. Watch entry status on Marks & results.",
                    "ExamSession", session.getId().toString());
        }
    }

    private void assertTeacherMayEnter(UUID tenantId, Student student, UUID subjectId) {
        if (TenantContext.getRole() != UserRole.TEACHER) {
            return;
        }
        UUID teacherId = TenantContext.getUserId();
        boolean allowed = assignmentRepository.findByTenantIdAndTeacherUserId(tenantId, teacherId).stream()
                .anyMatch(a -> subjectId.equals(a.getSubjectId())
                        && student.getClassId().equals(a.getClassId())
                        && (a.getSectionId() == null || a.getSectionId().equals(student.getSectionId())));
        if (!allowed) {
            throw new ForbiddenException("You can only enter marks for your assigned class, section, and subject");
        }
    }

    private boolean marksComplete(ExamSession session, Student student) {
        java.util.Set<UUID> expected = expectedSubjects(session, student);
        if (expected.isEmpty()) {
            return !resultRepository.findByTenantIdAndExamSessionIdAndStudentId(
                    session.getTenantId(), session.getId(), student.getId()).isEmpty();
        }
        java.util.Set<UUID> got = resultRepository.findByTenantIdAndExamSessionIdAndStudentId(
                        session.getTenantId(), session.getId(), student.getId())
                .stream().map(ExamResult::getSubjectId).collect(java.util.stream.Collectors.toSet());
        return got.containsAll(expected);
    }

    private java.util.Set<UUID> expectedSubjects(ExamSession session, Student student) {
        List<ExamDateSheetEntry> sheet = dateSheetRepository
                .findByTenantIdAndExamSessionIdAndClassIdOrderByExamDateAscStartTimeAsc(
                        session.getTenantId(), session.getId(), student.getClassId());
        if (!sheet.isEmpty()) {
            return sheet.stream()
                    .filter(e -> e.getSectionId() == null || e.getSectionId().equals(student.getSectionId()))
                    .map(ExamDateSheetEntry::getSubjectId)
                    .collect(java.util.stream.Collectors.toSet());
        }
        return classSubjectRepository.findByTenantIdAndClassId(session.getTenantId(), student.getClassId())
                .stream().map(ClassSubject::getSubjectId).collect(java.util.stream.Collectors.toSet());
    }

    private ExamSession requireSession(UUID id) {

        ExamSession s =
                sessionRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Exam session",
                                        id
                                )
                        );

        TenantGuard.assertSameTenant(
                s.getTenantId()
        );

        return s;
    }
}

