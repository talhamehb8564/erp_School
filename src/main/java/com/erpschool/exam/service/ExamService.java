package com.erpschool.exam.service;

import com.erpschool.academic.service.AcademicService;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.GradeCalculator;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.exam.entity.ExamResult;
import com.erpschool.exam.entity.ExamSession;
import com.erpschool.exam.repository.ExamResultRepository;
import com.erpschool.exam.repository.ExamSessionRepository;
import com.erpschool.notification.service.NotificationService;
import com.erpschool.student.entity.Student;
import com.erpschool.student.repository.ParentStudentRepository;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.student.service.StudentAccessService;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExamService {

    private final ExamSessionRepository sessionRepository;
    private final ExamResultRepository resultRepository;
    private final AcademicService academicService;
    private final StudentAccessService studentAccessService;
    private final StudentRepository studentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public ExamService(
            ExamSessionRepository sessionRepository,
            ExamResultRepository resultRepository,
            AcademicService academicService,
            StudentAccessService studentAccessService,
            StudentRepository studentRepository,
            ParentStudentRepository parentStudentRepository,
            NotificationService notificationService,
            UserRepository userRepository) {

        this.sessionRepository = sessionRepository;
        this.resultRepository = resultRepository;
        this.academicService = academicService;
        this.studentAccessService = studentAccessService;
        this.studentRepository = studentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Transactional
    public ExamSession createSession(ExamSession session) {

        session.setTenantId(
                TenantGuard.requireTenantId(null)
        );

        session.setPublished(false);

        session.setCreatedBy(
                TenantContext.getUserId()
        );

        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<ExamSession> sessions() {

        UUID tenantId = TenantGuard.requireTenantId(null);

        UserRole role = TenantContext.getRole();

        if (role == UserRole.PARENT
                || role == UserRole.STUDENT) {

            return sessionRepository
                    .findByTenantIdAndPublishedTrueOrderByCreatedAtDesc(
                            tenantId
                    );
        }

        return sessionRepository
                .findByTenantIdOrderByCreatedAtDesc(
                        tenantId
                );
    }

    @Transactional
    public ExamResult upsertResult(
            UUID sessionId,
            UUID studentId,
            UUID subjectId,
            BigDecimal total,
            BigDecimal obtained,
            String remarks) {

        UUID tenantId = TenantGuard.requireTenantId(null);

        ExamSession session = requireSession(sessionId);

        if (session.isPublished()) {
            throw new BusinessException(
                    "Cannot change results after they are published"
            );
        }

        studentAccessService.requireStudent(studentId);

        academicService.requireSubject(subjectId);

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

        if ((role == UserRole.PARENT
                || role == UserRole.STUDENT)
                && !session.isPublished()) {

            throw new ForbiddenException(
                    "Results are not published yet"
            );
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

