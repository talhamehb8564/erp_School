package com.erpschool.exam.online;

import com.erpschool.academic.service.AcademicService;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.GradeCalculator;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.student.entity.Student;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OnlineExamService {

    private final OnlineExamRepository examRepository;
    private final OnlineExamQuestionRepository questionRepository;
    private final OnlineExamAttemptRepository attemptRepository;
    private final OnlineExamAnswerRepository answerRepository;
    private final AcademicService academicService;
    private final StudentRepository studentRepository;
    private final StudentAccessService studentAccessService;
    private final UserRepository userRepository;

    public OnlineExamService(OnlineExamRepository examRepository,
                             OnlineExamQuestionRepository questionRepository,
                             OnlineExamAttemptRepository attemptRepository,
                             OnlineExamAnswerRepository answerRepository,
                             AcademicService academicService,
                             StudentRepository studentRepository,
                             StudentAccessService studentAccessService,
                             UserRepository userRepository) {
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.academicService = academicService;
        this.studentRepository = studentRepository;
        this.studentAccessService = studentAccessService;
        this.userRepository = userRepository;
    }

    @Transactional
    public Map<String, Object> create(OnlineExam incoming) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        assertStaffWrite();
        validateMeta(incoming);
        if (TenantContext.getRole() == UserRole.TEACHER) {
            academicService.assertTeacherAssigned(TenantContext.getUserId(),
                    incoming.getClassId(), incoming.getSectionId(), incoming.getSubjectId());
        }
        incoming.setTenantId(tenantId);
        incoming.setCreatedBy(TenantContext.getUserId());
        if (incoming.getCampusId() == null && CampusScope.restricts()) {
            incoming.setCampusId(CampusScope.current());
        }
        incoming.setStatus("DRAFT");
        incoming.setTotalMarks(BigDecimal.ZERO);
        return toStaffExam(examRepository.save(incoming), List.of());
    }

    @Transactional
    public Map<String, Object> update(UUID id, OnlineExam incoming) {
        OnlineExam exam = requireExam(id);
        assertCanManage(exam);
        if (!"DRAFT".equals(exam.getStatus()) && !"SCHEDULED".equals(exam.getStatus())) {
            throw new BusinessException("Only draft or scheduled exams can be edited");
        }
        validateMeta(incoming);
        if (TenantContext.getRole() == UserRole.TEACHER) {
            academicService.assertTeacherAssigned(TenantContext.getUserId(),
                    incoming.getClassId(), incoming.getSectionId(), incoming.getSubjectId());
        }
        exam.setTitle(incoming.getTitle());
        exam.setDescription(incoming.getDescription());
        exam.setClassId(incoming.getClassId());
        exam.setSectionId(incoming.getSectionId());
        exam.setSubjectId(incoming.getSubjectId());
        exam.setExamDate(incoming.getExamDate());
        exam.setStartAt(incoming.getStartAt());
        exam.setDurationMinutes(incoming.getDurationMinutes());
        exam.setPassingMarks(incoming.getPassingMarks());
        exam.setMcqMarks(nz(incoming.getMcqMarks(), BigDecimal.ONE));
        exam.setShortMarks(nz(incoming.getShortMarks(), BigDecimal.valueOf(2)));
        exam.setLongMarks(nz(incoming.getLongMarks(), BigDecimal.valueOf(5)));
        exam.setShortRequired(incoming.getShortRequired());
        exam.setLongRequired(incoming.getLongRequired());
        exam.setUpdatedBy(TenantContext.getUserId());
        return toStaffExam(examRepository.save(exam), questions(exam));
    }

    @Transactional
    public Map<String, Object> replaceQuestions(UUID id, List<Map<String, Object>> body) {
        OnlineExam exam = requireExam(id);
        assertCanManage(exam);
        if (!"DRAFT".equals(exam.getStatus())) {
            throw new BusinessException("Questions can only be changed while the exam is a draft");
        }
        UUID tenantId = exam.getTenantId();
        questionRepository.deleteByTenantIdAndExamId(tenantId, exam.getId());
        List<OnlineExamQuestion> saved = new ArrayList<>();
        int order = 0;
        BigDecimal total = BigDecimal.ZERO;
        if (body != null) {
            for (Map<String, Object> row : body) {
                OnlineExamQuestion q = new OnlineExamQuestion();
                q.setTenantId(tenantId);
                q.setExamId(exam.getId());
                q.setCreatedBy(TenantContext.getUserId());
                String type = str(row.get("questionType"), "MCQ").toUpperCase();
                if (!List.of("MCQ", "SHORT", "LONG").contains(type)) {
                    throw new BusinessException("Question type must be MCQ, SHORT or LONG");
                }
                q.setQuestionType(type);
                q.setPrompt(str(row.get("prompt"), ""));
                if (q.getPrompt().isBlank()) {
                    throw new BusinessException("Each question needs a prompt");
                }
                q.setMarks(marksFor(exam, type, row.get("marks")));
                q.setSortOrder(order++);
                q.setOptionA(str(row.get("optionA"), null));
                q.setOptionB(str(row.get("optionB"), null));
                q.setOptionC(str(row.get("optionC"), null));
                q.setOptionD(str(row.get("optionD"), null));
                q.setCorrectOption(normalizeOption(row.get("correctOption")));
                q.setReferenceAnswer(str(row.get("referenceAnswer"), null));
                if ("MCQ".equals(type) && (blank(q.getOptionA()) || blank(q.getOptionB()))) {
                    throw new BusinessException("MCQs need at least options A and B");
                }
                total = total.add(q.getMarks());
                saved.add(questionRepository.save(q));
            }
        }
        exam.setTotalMarks(total);
        exam.setUpdatedBy(TenantContext.getUserId());
        examRepository.save(exam);
        return toStaffExam(exam, saved);
    }

    @Transactional
    public Map<String, Object> schedule(UUID id) {
        OnlineExam exam = requireExam(id);
        assertCanManage(exam);
        if (questions(exam).isEmpty()) {
            throw new BusinessException("Add questions before scheduling");
        }
        exam.setStatus(computeStatus(exam, "SCHEDULED"));
        exam.setUpdatedBy(TenantContext.getUserId());
        return toStaffExam(examRepository.save(exam), questions(exam));
    }

    @Transactional
    public Map<String, Object> publishResults(UUID id) {
        OnlineExam exam = requireExam(id);
        assertCanManage(exam);
        exam.setStatus("RESULTS_PUBLISHED");
        exam.setPublishedAt(Instant.now());
        exam.setUpdatedBy(TenantContext.getUserId());
        return toStaffExam(examRepository.save(exam), questions(exam));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> staffList() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        assertStaffRead();
        List<OnlineExam> rows;
        UserRole role = TenantContext.getRole();
        if (role == UserRole.TEACHER) {
            rows = examRepository.findByTenantIdAndCreatedByOrderByStartAtDesc(tenantId, TenantContext.getUserId());
        } else if (CampusScope.restricts()) {
            rows = examRepository.findByTenantIdAndCampusIdOrderByStartAtDesc(tenantId, CampusScope.current());
        } else {
            rows = examRepository.findByTenantIdOrderByStartAtDesc(tenantId);
        }
        return rows.stream().map(this::toStaffSummary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> staffGet(UUID id) {
        OnlineExam exam = requireExam(id);
        assertCanViewStaff(exam);
        return toStaffExam(exam, questions(exam));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> studentList() {
        Student me = requireMeStudent();
        UUID tenantId = me.getTenantId();
        return examRepository.findByTenantIdAndClassIdAndSectionIdOrderByStartAtDesc(
                        tenantId, me.getClassId(), me.getSectionId())
                .stream()
                .filter(e -> !"DRAFT".equals(e.getStatus()))
                .map(e -> {
                    Map<String, Object> m = toStudentSummary(e);
                    attemptRepository.findByTenantIdAndExamIdAndStudentId(tenantId, e.getId(), me.getId())
                            .ifPresent(a -> {
                                m.put("attemptStatus", a.getStatus());
                                if ("RESULTS_PUBLISHED".equals(e.getStatus())) {
                                    m.put("obtainedMarks", a.getObtainedMarks());
                                    m.put("grade", a.getGrade());
                                }
                            });
                    return m;
                })
                .toList();
    }

    @Transactional
    public Map<String, Object> start(UUID id) {
        Student me = requireMeStudent();
        OnlineExam exam = requireExam(id);
        assertStudentAssigned(exam, me);
        Instant now = Instant.now();
        Instant end = examEnd(exam);
        if (now.isBefore(exam.getStartAt())) {
            throw new BusinessException("EXAM_NOT_STARTED", "Exam starts at " + exam.getStartAt());
        }
        if (!now.isBefore(end)) {
            throw new BusinessException("EXAM_CLOSED", "This exam is closed");
        }
        OnlineExamAttempt attempt = attemptRepository
                .findByTenantIdAndExamIdAndStudentId(exam.getTenantId(), exam.getId(), me.getId())
                .orElse(null);
        if (attempt == null) {
            attempt = new OnlineExamAttempt();
            attempt.setTenantId(exam.getTenantId());
            attempt.setExamId(exam.getId());
            attempt.setStudentId(me.getId());
            attempt.setStartedAt(now);
            attempt.setDeadlineAt(end);
            attempt.setStatus("IN_PROGRESS");
            attempt.setTotalMarks(exam.getTotalMarks());
            attempt.setCreatedBy(TenantContext.getUserId());
            attempt = attemptRepository.save(attempt);
        }
        enforceDeadline(exam, attempt);
        if (!"IN_PROGRESS".equals(attempt.getStatus())) {
            return studentResult(exam, attempt, me, false);
        }
        return studentPaper(exam, attempt, false);
    }

    @Transactional
    public Map<String, Object> saveAnswers(UUID id, List<Map<String, Object>> answers) {
        Student me = requireMeStudent();
        OnlineExam exam = requireExam(id);
        OnlineExamAttempt attempt = requireOwnAttempt(exam, me);
        enforceDeadline(exam, attempt);
        if (!"IN_PROGRESS".equals(attempt.getStatus())) {
            throw new BusinessException("EXAM_CLOSED", "This attempt is already submitted");
        }
        Map<UUID, OnlineExamQuestion> byId = questions(exam).stream()
                .collect(Collectors.toMap(OnlineExamQuestion::getId, q -> q));
        if (answers != null) {
            for (Map<String, Object> row : answers) {
                UUID qid = uuid(row.get("questionId"));
                OnlineExamQuestion q = byId.get(qid);
                if (q == null) continue;
                OnlineExamAnswer ans = answerRepository
                        .findByTenantIdAndAttemptIdAndQuestionId(exam.getTenantId(), attempt.getId(), qid)
                        .orElseGet(OnlineExamAnswer::new);
                if (ans.getId() == null) {
                    ans.setTenantId(exam.getTenantId());
                    ans.setAttemptId(attempt.getId());
                    ans.setQuestionId(qid);
                    ans.setCreatedBy(TenantContext.getUserId());
                }
                if ("MCQ".equals(q.getQuestionType())) {
                    ans.setOptionChoice(normalizeOption(row.get("optionChoice")));
                    ans.setTextAnswer(null);
                } else {
                    ans.setTextAnswer(str(row.get("textAnswer"), null));
                    ans.setOptionChoice(null);
                }
                ans.setUpdatedBy(TenantContext.getUserId());
                answerRepository.save(ans);
            }
        }
        return studentPaper(exam, attempt, false);
    }

    @Transactional
    public Map<String, Object> submit(UUID id, boolean auto) {
        Student me = requireMeStudent();
        OnlineExam exam = requireExam(id);
        OnlineExamAttempt attempt = requireOwnAttempt(exam, me);
        if ("IN_PROGRESS".equals(attempt.getStatus())) {
            finalizeSubmit(exam, attempt, auto);
        }
        return studentResult(exam, attempt, me, false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> studentResult(UUID id) {
        Student me = requireMeStudent();
        OnlineExam exam = requireExam(id);
        OnlineExamAttempt attempt = requireOwnAttempt(exam, me);
        return studentResult(exam, attempt, me, false);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> attempts(UUID id) {
        OnlineExam exam = requireExam(id);
        assertCanViewStaff(exam);
        List<OnlineExamAttempt> rows = attemptRepository.findByTenantIdAndExamIdOrderBySubmittedAtDesc(
                exam.getTenantId(), exam.getId());
        Map<UUID, Student> students = studentRepository.findAllById(
                        rows.stream().map(OnlineExamAttempt::getStudentId).toList())
                .stream().collect(Collectors.toMap(Student::getId, s -> s, (a, b) -> a));
        Map<UUID, String> names = new HashMap<>();
        for (Student s : students.values()) {
            if (s.getUserId() != null) {
                userRepository.findById(s.getUserId()).map(User::getFullName)
                        .ifPresent(n -> names.put(s.getId(), n));
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (OnlineExamAttempt a : rows) {
            Student s = students.get(a.getStudentId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("studentId", a.getStudentId());
            m.put("studentName", names.getOrDefault(a.getStudentId(), "—"));
            m.put("rollNumber", s == null ? null : s.getRollNumber());
            m.put("status", a.getStatus());
            m.put("submittedAt", a.getSubmittedAt());
            m.put("autoSubmitted", a.isAutoSubmitted());
            m.put("obtainedMarks", a.getObtainedMarks());
            m.put("totalMarks", a.getTotalMarks());
            m.put("percentage", a.getPercentage());
            m.put("grade", a.getGrade());
            out.add(m);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> attemptDetail(UUID examId, UUID attemptId) {
        OnlineExam exam = requireExam(examId);
        assertCanViewStaff(exam);
        OnlineExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt", attemptId));
        TenantGuard.assertSameTenant(attempt.getTenantId());
        if (!attempt.getExamId().equals(exam.getId())) {
            throw new ResourceNotFoundException("Attempt", attemptId);
        }
        Student student = studentAccessService.requireStudent(attempt.getStudentId());
        return staffAttempt(exam, attempt, student);
    }

    @Transactional
    public Map<String, Object> grade(UUID examId, UUID attemptId, List<Map<String, Object>> marks) {
        OnlineExam exam = requireExam(examId);
        assertCanManage(exam);
        OnlineExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt", attemptId));
        TenantGuard.assertSameTenant(attempt.getTenantId());
        if ("IN_PROGRESS".equals(attempt.getStatus())) {
            throw new BusinessException("Student has not submitted yet");
        }
        Map<UUID, OnlineExamQuestion> byId = questions(exam).stream()
                .collect(Collectors.toMap(OnlineExamQuestion::getId, q -> q));
        if (marks != null) {
            for (Map<String, Object> row : marks) {
                UUID qid = uuid(row.get("questionId"));
                OnlineExamQuestion q = byId.get(qid);
                if (q == null || "MCQ".equals(q.getQuestionType())) continue;
                OnlineExamAnswer ans = answerRepository
                        .findByTenantIdAndAttemptIdAndQuestionId(exam.getTenantId(), attempt.getId(), qid)
                        .orElse(null);
                if (ans == null) continue;
                BigDecimal obtained = decimal(row.get("obtainedMarks"));
                if (obtained == null) continue;
                if (obtained.compareTo(BigDecimal.ZERO) < 0 || obtained.compareTo(q.getMarks()) > 0) {
                    throw new BusinessException("Obtained marks must be between 0 and " + q.getMarks());
                }
                ans.setObtainedMarks(obtained);
                ans.setAutoGraded(false);
                ans.setUpdatedBy(TenantContext.getUserId());
                answerRepository.save(ans);
            }
        }
        tally(exam, attempt, true);
        attempt.setStatus("GRADED");
        attempt.setUpdatedBy(TenantContext.getUserId());
        attemptRepository.save(attempt);
        Student student = studentAccessService.requireStudent(attempt.getStudentId());
        return staffAttempt(exam, attempt, student);
    }

    private void finalizeSubmit(OnlineExam exam, OnlineExamAttempt attempt, boolean auto) {
        List<OnlineExamQuestion> qs = questions(exam);
        Map<UUID, OnlineExamAnswer> answers = answersOf(attempt);
        int shortCount = 0;
        int longCount = 0;
        int shortAnswered = 0;
        int longAnswered = 0;
        for (OnlineExamQuestion q : qs) {
            OnlineExamAnswer ans = answers.get(q.getId());
            if ("SHORT".equals(q.getQuestionType())) {
                shortCount++;
                if (ans != null && !blank(ans.getTextAnswer())) shortAnswered++;
            } else if ("LONG".equals(q.getQuestionType())) {
                longCount++;
                if (ans != null && !blank(ans.getTextAnswer())) longAnswered++;
            }
        }
        if (!auto) {
            int shortNeed = exam.getShortRequired() == null ? shortCount : exam.getShortRequired();
            int longNeed = exam.getLongRequired() == null ? longCount : exam.getLongRequired();
            if (shortAnswered < shortNeed) {
                throw new BusinessException("Attempt any " + shortNeed + " of " + shortCount + " short questions");
            }
            if (longAnswered < longNeed) {
                throw new BusinessException("Attempt any " + longNeed + " of " + longCount + " long questions");
            }
        }
        boolean pendingManual = false;
        for (OnlineExamQuestion q : qs) {
            OnlineExamAnswer ans = answers.get(q.getId());
            if ("MCQ".equals(q.getQuestionType())) {
                if (ans == null) continue;
                if (!blank(q.getCorrectOption()) && !blank(ans.getOptionChoice())) {
                    boolean ok = q.getCorrectOption().equalsIgnoreCase(ans.getOptionChoice());
                    ans.setObtainedMarks(ok ? q.getMarks() : BigDecimal.ZERO);
                    ans.setAutoGraded(true);
                    answerRepository.save(ans);
                }
            } else if (ans != null && !blank(ans.getTextAnswer()) && ans.getObtainedMarks() == null) {
                pendingManual = true;
            }
        }
        tally(exam, attempt, false);
        attempt.setSubmittedAt(Instant.now());
        attempt.setAutoSubmitted(auto);
        attempt.setStatus(pendingManual ? "SUBMITTED" : "GRADED");
        attempt.setUpdatedBy(TenantContext.getUserId());
        attemptRepository.save(attempt);
    }

    private void tally(OnlineExam exam, OnlineExamAttempt attempt, boolean finalizeManualZeros) {
        List<OnlineExamQuestion> qs = questions(exam);
        Map<UUID, OnlineExamAnswer> answers = answersOf(attempt);
        BigDecimal mcq = BigDecimal.ZERO;
        BigDecimal sh = BigDecimal.ZERO;
        BigDecimal lg = BigDecimal.ZERO;
        BigDecimal mcqTotal = BigDecimal.ZERO;
        BigDecimal shortTotal = BigDecimal.ZERO;
        BigDecimal longTotal = BigDecimal.ZERO;
        for (OnlineExamQuestion q : qs) {
            OnlineExamAnswer ans = answers.get(q.getId());
            BigDecimal got = ans == null ? null : ans.getObtainedMarks();
            if ("MCQ".equals(q.getQuestionType())) {
                mcqTotal = mcqTotal.add(q.getMarks());
                if (got != null) mcq = mcq.add(got);
            } else if ("SHORT".equals(q.getQuestionType())) {
                boolean attempted = ans != null && !blank(ans.getTextAnswer());
                if (attempted || exam.getShortRequired() == null) {
                    shortTotal = shortTotal.add(q.getMarks());
                }
                if (got != null) sh = sh.add(got);
                else if (finalizeManualZeros && attempted) {
                    /* pending */
                }
            } else {
                boolean attempted = ans != null && !blank(ans.getTextAnswer());
                if (attempted || exam.getLongRequired() == null) {
                    longTotal = longTotal.add(q.getMarks());
                }
                if (got != null) lg = lg.add(got);
            }
        }
        BigDecimal obtained = mcq.add(sh).add(lg);
        BigDecimal total = exam.getTotalMarks() == null ? mcqTotal.add(shortTotal).add(longTotal) : exam.getTotalMarks();
        BigDecimal pct = GradeCalculator.percentage(obtained, total);
        attempt.setMcqObtained(mcq);
        attempt.setShortObtained(sh);
        attempt.setLongObtained(lg);
        attempt.setObtainedMarks(obtained);
        attempt.setTotalMarks(total);
        attempt.setPercentage(pct);
        attempt.setGrade(GradeCalculator.grade(pct));
        String pass = GradeCalculator.passStatus(pct);
        if (exam.getPassingMarks() != null && obtained.compareTo(exam.getPassingMarks()) < 0) {
            pass = "FAIL";
        }
        attempt.setPassStatus(pass);
    }

    private void enforceDeadline(OnlineExam exam, OnlineExamAttempt attempt) {
        Instant now = Instant.now();
        if ("IN_PROGRESS".equals(attempt.getStatus())
                && (now.isAfter(attempt.getDeadlineAt()) || !now.isBefore(examEnd(exam)))) {
            finalizeSubmit(exam, attempt, true);
        }
    }

    private Instant examEnd(OnlineExam exam) {
        return exam.getStartAt().plus(exam.getDurationMinutes(), ChronoUnit.MINUTES);
    }

    private String computeStatus(OnlineExam exam, String fallback) {
        if ("RESULTS_PUBLISHED".equals(exam.getStatus())) return "RESULTS_PUBLISHED";
        if ("DRAFT".equals(exam.getStatus()) && "DRAFT".equals(fallback)) return "DRAFT";
        Instant now = Instant.now();
        Instant end = examEnd(exam);
        if (now.isBefore(exam.getStartAt())) return "SCHEDULED";
        if (now.isBefore(end)) return "LIVE";
        return "CLOSED";
    }

    private OnlineExam requireExam(UUID id) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        OnlineExam exam = examRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Online exam", id));
        TenantGuard.assertSameTenant(exam.getTenantId());
        return exam;
    }

    private List<OnlineExamQuestion> questions(OnlineExam exam) {
        return questionRepository.findByTenantIdAndExamIdOrderBySortOrderAsc(exam.getTenantId(), exam.getId());
    }

    private Map<UUID, OnlineExamAnswer> answersOf(OnlineExamAttempt attempt) {
        return answerRepository.findByTenantIdAndAttemptId(attempt.getTenantId(), attempt.getId())
                .stream().collect(Collectors.toMap(OnlineExamAnswer::getQuestionId, a -> a, (a, b) -> a));
    }

    private Student requireMeStudent() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        return studentRepository.findByTenantIdAndUserId(tenantId, TenantContext.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
    }

    private OnlineExamAttempt requireOwnAttempt(OnlineExam exam, Student me) {
        return attemptRepository.findByTenantIdAndExamIdAndStudentId(exam.getTenantId(), exam.getId(), me.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
    }

    private void assertStudentAssigned(OnlineExam exam, Student me) {
        if (!exam.getClassId().equals(me.getClassId()) || !exam.getSectionId().equals(me.getSectionId())) {
            throw new ForbiddenException("This exam is not assigned to your class");
        }
        if (exam.getCampusId() != null && me.getCampusId() != null && !exam.getCampusId().equals(me.getCampusId())) {
            throw new ForbiddenException("This exam is not assigned to your campus");
        }
    }

    private void assertStaffWrite() {
        UserRole role = TenantContext.getRole();
        if (role != UserRole.SCHOOL_ADMIN && role != UserRole.PRINCIPAL
                && role != UserRole.TEACHER && role != UserRole.ERP_OWNER) {
            throw new ForbiddenException("Not allowed to manage online exams");
        }
    }

    private void assertStaffRead() {
        assertStaffWrite();
    }

    private void assertCanManage(OnlineExam exam) {
        assertStaffWrite();
        UserRole role = TenantContext.getRole();
        if (role == UserRole.TEACHER && !TenantContext.getUserId().equals(exam.getCreatedBy())) {
            throw new ForbiddenException("Teachers can only manage their own exams");
        }
        if (CampusScope.restricts() && exam.getCampusId() != null
                && !exam.getCampusId().equals(CampusScope.current())) {
            throw new ForbiddenException("This exam belongs to another campus");
        }
    }

    private void assertCanViewStaff(OnlineExam exam) {
        assertStaffRead();
        UserRole role = TenantContext.getRole();
        if (role == UserRole.TEACHER && !TenantContext.getUserId().equals(exam.getCreatedBy())) {
            throw new ForbiddenException("Teachers can only view their own exams");
        }
    }

    private void validateMeta(OnlineExam incoming) {
        if (incoming.getTitle() == null || incoming.getTitle().isBlank()) {
            throw new BusinessException("Exam title is required");
        }
        academicService.requireClass(incoming.getClassId());
        academicService.requireSection(incoming.getSectionId());
        academicService.requireSubject(incoming.getSubjectId());
        if (incoming.getStartAt() == null) {
            throw new BusinessException("Start date/time is required");
        }
        if (incoming.getDurationMinutes() <= 0) {
            throw new BusinessException("Duration must be greater than 0");
        }
        incoming.setMcqMarks(nz(incoming.getMcqMarks(), BigDecimal.ONE));
        incoming.setShortMarks(nz(incoming.getShortMarks(), BigDecimal.valueOf(2)));
        incoming.setLongMarks(nz(incoming.getLongMarks(), BigDecimal.valueOf(5)));
    }

    private Map<String, Object> toStaffSummary(OnlineExam exam) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", exam.getId());
        m.put("title", exam.getTitle());
        m.put("classId", exam.getClassId());
        m.put("sectionId", exam.getSectionId());
        m.put("subjectId", exam.getSubjectId());
        m.put("startAt", exam.getStartAt());
        m.put("durationMinutes", exam.getDurationMinutes());
        m.put("totalMarks", exam.getTotalMarks());
        m.put("status", computeStatus(exam, exam.getStatus()));
        return m;
    }

    private Map<String, Object> toStaffExam(OnlineExam exam, List<OnlineExamQuestion> qs) {
        Map<String, Object> m = toStaffSummary(exam);
        m.put("description", exam.getDescription());
        m.put("examDate", exam.getExamDate());
        m.put("passingMarks", exam.getPassingMarks());
        m.put("mcqMarks", exam.getMcqMarks());
        m.put("shortMarks", exam.getShortMarks());
        m.put("longMarks", exam.getLongMarks());
        m.put("shortRequired", exam.getShortRequired());
        m.put("longRequired", exam.getLongRequired());
        m.put("campusId", exam.getCampusId());
        m.put("questions", qs.stream().map(this::staffQuestion).toList());
        return m;
    }

    private Map<String, Object> staffQuestion(OnlineExamQuestion q) {
        Map<String, Object> m = studentQuestion(q);
        m.put("correctOption", q.getCorrectOption());
        m.put("referenceAnswer", q.getReferenceAnswer());
        return m;
    }

    private Map<String, Object> studentQuestion(OnlineExamQuestion q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.getId());
        m.put("questionType", q.getQuestionType());
        m.put("prompt", q.getPrompt());
        m.put("marks", q.getMarks());
        m.put("sortOrder", q.getSortOrder());
        if ("MCQ".equals(q.getQuestionType())) {
            m.put("optionA", q.getOptionA());
            m.put("optionB", q.getOptionB());
            m.put("optionC", q.getOptionC());
            m.put("optionD", q.getOptionD());
        }
        return m;
    }

    private Map<String, Object> toStudentSummary(OnlineExam exam) {
        Instant now = Instant.now();
        Instant end = examEnd(exam);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", exam.getId());
        m.put("title", exam.getTitle());
        m.put("description", exam.getDescription());
        m.put("classId", exam.getClassId());
        m.put("sectionId", exam.getSectionId());
        m.put("subjectId", exam.getSubjectId());
        m.put("startAt", exam.getStartAt());
        m.put("durationMinutes", exam.getDurationMinutes());
        m.put("totalMarks", exam.getTotalMarks());
        m.put("status", computeStatus(exam, exam.getStatus()));
        m.put("serverNow", now);
        m.put("deadlineAt", end);
        m.put("remainingSeconds", Math.max(0, ChronoUnit.SECONDS.between(now, end)));
        m.put("shortRequired", exam.getShortRequired());
        m.put("longRequired", exam.getLongRequired());
        return m;
    }

    private Map<String, Object> studentPaper(OnlineExam exam, OnlineExamAttempt attempt, boolean withKeys) {
        List<OnlineExamQuestion> qs = questions(exam);
        Map<UUID, OnlineExamAnswer> answers = answersOf(attempt);
        Instant now = Instant.now();
        Map<String, Object> m = toStudentSummary(exam);
        m.put("attemptId", attempt.getId());
        m.put("attemptStatus", attempt.getStatus());
        m.put("deadlineAt", attempt.getDeadlineAt());
        m.put("remainingSeconds", Math.max(0, ChronoUnit.SECONDS.between(now, attempt.getDeadlineAt())));
        m.put("serverNow", now);
        int shorts = (int) qs.stream().filter(q -> "SHORT".equals(q.getQuestionType())).count();
        int longs = (int) qs.stream().filter(q -> "LONG".equals(q.getQuestionType())).count();
        m.put("shortCount", shorts);
        m.put("longCount", longs);
        m.put("shortRule", exam.getShortRequired() == null ? null : "Attempt any " + exam.getShortRequired() + " of " + shorts);
        m.put("longRule", exam.getLongRequired() == null ? null : "Attempt any " + exam.getLongRequired() + " of " + longs);
        List<Map<String, Object>> qout = new ArrayList<>();
        for (OnlineExamQuestion q : qs) {
            Map<String, Object> row = withKeys ? staffQuestion(q) : studentQuestion(q);
            OnlineExamAnswer ans = answers.get(q.getId());
            if (ans != null) {
                row.put("optionChoice", ans.getOptionChoice());
                row.put("textAnswer", ans.getTextAnswer());
            }
            qout.add(row);
        }
        m.put("questions", qout);
        return m;
    }

    private Map<String, Object> studentResult(OnlineExam exam, OnlineExamAttempt attempt, Student me, boolean withKeys) {
        Map<String, Object> m = toStudentSummary(exam);
        m.put("attemptId", attempt.getId());
        m.put("attemptStatus", attempt.getStatus());
        m.put("submittedAt", attempt.getSubmittedAt());
        m.put("autoSubmitted", attempt.isAutoSubmitted());
        m.put("studentId", me.getId());
        m.put("rollNumber", me.getRollNumber());
        if ("IN_PROGRESS".equals(attempt.getStatus())) {
            return studentPaper(exam, attempt, false);
        }
        boolean published = "RESULTS_PUBLISHED".equals(exam.getStatus());
        if (!published) {
            if ("SUBMITTED".equals(attempt.getStatus())) {
                m.put("message", "Submitted — awaiting teacher evaluation");
            } else {
                m.put("message", "Submitted — results not published yet");
            }
            m.put("totalMarks", attempt.getTotalMarks());
            return m;
        }
        if ("SUBMITTED".equals(attempt.getStatus())) {
            m.put("message", "Submitted — awaiting teacher evaluation");
            m.put("totalMarks", attempt.getTotalMarks());
            return m;
        }
        m.put("mcqObtained", attempt.getMcqObtained());
        m.put("shortObtained", attempt.getShortObtained());
        m.put("longObtained", attempt.getLongObtained());
        m.put("obtainedMarks", attempt.getObtainedMarks());
        m.put("totalMarks", attempt.getTotalMarks());
        m.put("percentage", attempt.getPercentage());
        m.put("grade", attempt.getGrade());
        m.put("passStatus", attempt.getPassStatus());
        if (withKeys) {
            m.put("questions", questions(exam).stream().map(this::staffQuestion).toList());
        }
        return m;
    }

    private Map<String, Object> staffAttempt(OnlineExam exam, OnlineExamAttempt attempt, Student student) {
        Map<String, Object> m = studentPaper(exam, attempt, true);
        m.put("studentId", student.getId());
        m.put("rollNumber", student.getRollNumber());
        m.put("obtainedMarks", attempt.getObtainedMarks());
        m.put("mcqObtained", attempt.getMcqObtained());
        m.put("shortObtained", attempt.getShortObtained());
        m.put("longObtained", attempt.getLongObtained());
        m.put("percentage", attempt.getPercentage());
        m.put("grade", attempt.getGrade());
        m.put("passStatus", attempt.getPassStatus());
        m.put("submittedAt", attempt.getSubmittedAt());
        Map<UUID, OnlineExamAnswer> answers = answersOf(attempt);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> qs = (List<Map<String, Object>>) m.get("questions");
        for (Map<String, Object> q : qs) {
            UUID qid = (UUID) q.get("id");
            OnlineExamAnswer ans = answers.get(qid);
            if (ans != null) {
                q.put("obtainedMarks", ans.getObtainedMarks());
                q.put("autoGraded", ans.isAutoGraded());
            }
        }
        return m;
    }

    private static BigDecimal marksFor(OnlineExam exam, String type, Object override) {
        BigDecimal custom = decimal(override);
        if (custom != null && custom.compareTo(BigDecimal.ZERO) > 0) return custom;
        return switch (type) {
            case "SHORT" -> nz(exam.getShortMarks(), BigDecimal.valueOf(2));
            case "LONG" -> nz(exam.getLongMarks(), BigDecimal.valueOf(5));
            default -> nz(exam.getMcqMarks(), BigDecimal.ONE);
        };
    }

    private static BigDecimal nz(BigDecimal v, BigDecimal d) {
        return v == null ? d : v;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String str(Object v, String d) {
        if (v == null) return d;
        String s = v.toString().trim();
        return s.isEmpty() ? d : s;
    }

    private static String normalizeOption(Object v) {
        String s = str(v, null);
        if (s == null) return null;
        s = s.toUpperCase();
        if (s.length() != 1 || s.charAt(0) < 'A' || s.charAt(0) > 'D') {
            throw new BusinessException("Option must be A, B, C or D");
        }
        return s;
    }

    private static UUID uuid(Object v) {
        if (v == null) return null;
        try {
            return UUID.fromString(v.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal decimal(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
