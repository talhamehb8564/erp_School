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
import com.erpschool.user.entity.UserRole;
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

    public ExamService(ExamSessionRepository sessionRepository,
                       ExamResultRepository resultRepository,
                       AcademicService academicService,
                       StudentAccessService studentAccessService,
                       StudentRepository studentRepository,
                       ParentStudentRepository parentStudentRepository,
                       NotificationService notificationService) {
        this.sessionRepository = sessionRepository;
        this.resultRepository = resultRepository;
        this.academicService = academicService;
        this.studentAccessService = studentAccessService;
        this.studentRepository = studentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public ExamSession createSession(ExamSession session) {
        session.setTenantId(TenantGuard.requireTenantId(null));
        session.setPublished(false);
        session.setCreatedBy(TenantContext.getUserId());
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<ExamSession> sessions() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        UserRole role = TenantContext.getRole();
        if (role == UserRole.PARENT || role == UserRole.STUDENT) {
            return sessionRepository.findByTenantIdAndPublishedTrueOrderByCreatedAtDesc(tenantId);
        }
        return sessionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public ExamResult upsertResult(UUID sessionId, UUID studentId, UUID subjectId,
                                   BigDecimal total, BigDecimal obtained, String remarks) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        ExamSession session = requireSession(sessionId);
        if (session.isPublished()) {
            throw new BusinessException("Cannot change results after they are published");
        }
        studentAccessService.requireStudent(studentId);
        academicService.requireSubject(subjectId);
        if (obtained.compareTo(total) > 0) {
            throw new BusinessException("Obtained marks cannot exceed total marks");
        }
        BigDecimal pct = GradeCalculator.percentage(obtained, total);
        ExamResult row = resultRepository
                .findByTenantIdAndExamSessionIdAndStudentIdAndSubjectId(tenantId, sessionId, studentId, subjectId)
                .orElseGet(ExamResult::new);
        row.setTenantId(tenantId);
        row.setExamSessionId(sessionId);
        row.setStudentId(studentId);
        row.setSubjectId(subjectId);
        row.setTotalMarks(total);
        row.setObtainedMarks(obtained);
        row.setPercentage(pct);
        row.setGrade(GradeCalculator.grade(pct));
        row.setPassStatus(GradeCalculator.passStatus(pct));
        row.setRemarks(remarks);
        if (row.getId() == null) {
            row.setCreatedBy(TenantContext.getUserId());
        } else {
            row.setUpdatedBy(TenantContext.getUserId());
        }
        return resultRepository.save(row);
    }

    @Transactional
    public ExamSession publish(UUID sessionId) {
        ExamSession session = requireSession(sessionId);
        session.setPublished(true);
        session.setPublishedAt(Instant.now());
        session.setUpdatedBy(TenantContext.getUserId());
        session = sessionRepository.save(session);
        resultRepository.findByTenantIdAndExamSessionId(session.getTenantId(), sessionId)
                .forEach(r -> {
                    Student st = studentRepository.findById(r.getStudentId()).orElse(null);
                    if (st != null) {
                        notificationService.notifyUser(session.getTenantId(), st.getUserId(), "RESULT",
                                "Result published: " + session.getName(),
                                "Grade " + r.getGrade() + " (" + r.getPercentage() + "%)",
                                "ExamSession", session.getId().toString());
                        parentStudentRepository.findByTenantIdAndStudentId(session.getTenantId(), st.getId())
                                .forEach(link -> notificationService.notifyUser(session.getTenantId(),
                                        link.getParentUserId(), "RESULT",
                                        "Result published: " + session.getName(),
                                        "Your child's result is available.",
                                        "ExamSession", session.getId().toString()));
                    }
                });
        return session;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studentResult(UUID sessionId, UUID studentId) {
        ExamSession session = requireSession(sessionId);
        Student student = studentAccessService.requireStudent(studentId);
        UserRole role = TenantContext.getRole();
        if ((role == UserRole.PARENT || role == UserRole.STUDENT) && !session.isPublished()) {
            throw new ForbiddenException("Results are not published yet");
        }
        List<ExamResult> rows = resultRepository.findByTenantIdAndExamSessionIdAndStudentId(
                session.getTenantId(), sessionId, student.getId());
        BigDecimal total = rows.stream().map(ExamResult::getTotalMarks).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal obtained = rows.stream().map(ExamResult::getObtainedMarks).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pct = GradeCalculator.percentage(obtained, total);
        Map<String, Object> m = new HashMap<>();
        m.put("session", session);
        m.put("studentId", student.getId());
        m.put("subjects", rows);
        m.put("totalMarks", total);
        m.put("obtainedMarks", obtained);
        m.put("percentage", pct);
        m.put("grade", GradeCalculator.grade(pct));
        m.put("passStatus", GradeCalculator.passStatus(pct));
        return m;
    }

    @Transactional(readOnly = true)
    public List<ExamResult> sessionResults(UUID sessionId) {
        ExamSession session = requireSession(sessionId);
        return resultRepository.findByTenantIdAndExamSessionId(session.getTenantId(), sessionId);
    }

    private ExamSession requireSession(UUID id) {
        ExamSession s = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam session", id));
        TenantGuard.assertSameTenant(s.getTenantId());
        return s;
    }
}
