package com.erpschool.exam.online;

import com.erpschool.academic.entity.Section;
import com.erpschool.academic.repository.SectionRepository;
import com.erpschool.academic.service.AcademicService;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.GradeCalculator;
import com.erpschool.common.util.Instants;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OnlineExamService {

    private static final ZoneId KARACHI = ZoneId.of("Asia/Karachi");

    private final OnlineExamRepository examRepository;
    private final OnlineExamQuestionRepository questionRepository;
    private final OnlineExamAttemptRepository attemptRepository;
    private final OnlineExamAnswerRepository answerRepository;
    private final OnlineExamSectionRepository examSectionRepository;
    private final SectionRepository sectionRepository;
    private final AcademicService academicService;
    private final StudentRepository studentRepository;
    private final StudentAccessService studentAccessService;
    private final UserRepository userRepository;

    public OnlineExamService(OnlineExamRepository examRepository,
                             OnlineExamQuestionRepository questionRepository,
                             OnlineExamAttemptRepository attemptRepository,
                             OnlineExamAnswerRepository answerRepository,
                             OnlineExamSectionRepository examSectionRepository,
                             SectionRepository sectionRepository,
                             AcademicService academicService,
                             StudentRepository studentRepository,
                             StudentAccessService studentAccessService,
                             UserRepository userRepository) {
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.examSectionRepository = examSectionRepository;
        this.sectionRepository = sectionRepository;
        this.academicService = academicService;
        this.studentRepository = studentRepository;
        this.studentAccessService = studentAccessService;
        this.userRepository = userRepository;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        assertStaffWrite();
        OnlineExam incoming = new OnlineExam();
        applyBody(incoming, body);
        List<UUID> sections = resolveSections(incoming.getClassId(), body);
        validateMeta(incoming, sections);
        assertTeacherCanWrite(incoming.getClassId(), incoming.getSubjectId(), sections);
        incoming.setTenantId(tenantId);
        incoming.setCreatedBy(TenantContext.getUserId());
        if (incoming.getCampusId() == null && CampusScope.restricts()) {
            incoming.setCampusId(CampusScope.current());
        }
        incoming.setStatus("DRAFT");
        incoming.setTotalMarks(BigDecimal.ZERO);
        incoming.setSectionId(sections.get(0));
        OnlineExam saved = examRepository.save(incoming);
        replaceSections(saved, sections);
        return toStaffExam(saved, List.of());
    }

    @Transactional
    public Map<String, Object> update(UUID id, Map<String, Object> body) {
        OnlineExam exam = requireExam(id);
        assertCanManage(exam);
        if (!"DRAFT".equals(exam.getStatus()) && !"SCHEDULED".equals(exam.getStatus())) {
            throw new BusinessException("Only draft or scheduled exams can be edited");
        }
        OnlineExam incoming = new OnlineExam();
        incoming.setTitle(exam.getTitle());
        incoming.setDescription(exam.getDescription());
        incoming.setClassId(exam.getClassId());
        incoming.setSectionId(exam.getSectionId());
        incoming.setSubjectId(exam.getSubjectId());
        incoming.setExamDate(exam.getExamDate());
        incoming.setStartAt(exam.getStartAt());
        incoming.setDurationMinutes(exam.getDurationMinutes());
        incoming.setPassingMarks(exam.getPassingMarks());
        incoming.setMcqMarks(exam.getMcqMarks());
        incoming.setShortMarks(exam.getShortMarks());
        incoming.setLongMarks(exam.getLongMarks());
        incoming.setShortRequired(exam.getShortRequired());
        incoming.setLongRequired(exam.getLongRequired());
        applyBody(incoming, body);
        List<UUID> sections = resolveSections(incoming.getClassId(), body);
        validateMeta(incoming, sections);
        assertTeacherCanWrite(incoming.getClassId(), incoming.getSubjectId(), sections);
        List<UUID> currentSections = sectionsOf(exam);
        boolean assignmentChanged = !exam.getClassId().equals(incoming.getClassId())
                || !sameIds(currentSections, sections);
        if (assignmentChanged && attemptRepository.existsByTenantIdAndExamId(exam.getTenantId(), exam.getId())) {
            throw new BusinessException(
                    "Class and sections cannot be changed after students have started this exam");
        }
        exam.setTitle(incoming.getTitle());
        exam.setDescription(incoming.getDescription());
        exam.setClassId(incoming.getClassId());
        exam.setSectionId(sections.get(0));
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
        OnlineExam saved = examRepository.save(exam);
        replaceSections(saved, sections);
        return toStaffExam(saved, questions(saved));
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
        Map<UUID, List<UUID>> sectionMap = sectionMap(rows);
        return rows.stream()
                .map(e -> toStaffSummary(e, sectionMap.getOrDefault(e.getId(), fallbackSections(e))))
                .toList();
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
        List<OnlineExam> rows = examSectionRepository.findAssigned(tenantId, me.getClassId(), me.getSectionId())
                .stream().filter(e -> !"DRAFT".equals(e.getStatus())).toList();
        Map<UUID, List<UUID>> sectionMap = sectionMap(rows);
        Map<UUID, OnlineExamAttempt> attempts = attemptRepository
                .findByTenantIdAndStudentIdOrderByStartedAtDesc(tenantId, me.getId())
                .stream()
                .collect(Collectors.toMap(OnlineExamAttempt::getExamId, a -> a, (a, b) -> a));
        Map<UUID, List<OnlineExamQuestion>> questionsByExam = Map.of();
        Map<UUID, Map<UUID, OnlineExamAnswer>> answersByAttempt = Map.of();
        if (!rows.isEmpty()) {
            questionsByExam = questionRepository.findByTenantIdAndExamIdIn(
                            tenantId, rows.stream().map(OnlineExam::getId).toList())
                    .stream().collect(Collectors.groupingBy(OnlineExamQuestion::getExamId));
            List<UUID> attemptIds = rows.stream()
                    .map(e -> attempts.get(e.getId()))
                    .filter(a -> a != null)
                    .map(OnlineExamAttempt::getId)
                    .toList();
            if (!attemptIds.isEmpty()) {
                answersByAttempt = answerRepository.findByTenantIdAndAttemptIdIn(tenantId, attemptIds)
                        .stream()
                        .collect(Collectors.groupingBy(OnlineExamAnswer::getAttemptId,
                                Collectors.toMap(OnlineExamAnswer::getQuestionId, a -> a, (a, b) -> a)));
            }
        }
        Map<UUID, List<OnlineExamQuestion>> qMap = questionsByExam;
        Map<UUID, Map<UUID, OnlineExamAnswer>> aMap = answersByAttempt;
        return rows.stream()
                .map(e -> {
                    Map<String, Object> m = toStudentSummary(e, sectionMap.getOrDefault(e.getId(), fallbackSections(e)));
                    OnlineExamAttempt a = attempts.get(e.getId());
                    if (a != null) {
                        m.put("attemptStatus", a.getStatus());
                        attachReleasedGrade(m, e, a,
                                qMap.getOrDefault(e.getId(), List.of()),
                                aMap.getOrDefault(a.getId(), Map.of()));
                    }
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
        List<UUID> userIds = students.values().stream().map(Student::getUserId).filter(u -> u != null).toList();
        if (!userIds.isEmpty()) {
            for (User u : userRepository.findAllById(userIds)) {
                for (Student s : students.values()) {
                    if (u.getId().equals(s.getUserId())) {
                        names.put(s.getId(), u.getFullName());
                    }
                }
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
                if (q == null) continue;
                if ("MCQ".equals(q.getQuestionType()) && !blank(q.getCorrectOption())) continue;
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
                if (!blank(q.getCorrectOption())) {
                    if (ans != null && !blank(ans.getOptionChoice())) {
                        boolean ok = q.getCorrectOption().equalsIgnoreCase(ans.getOptionChoice());
                        ans.setObtainedMarks(ok ? q.getMarks() : BigDecimal.ZERO);
                        ans.setAutoGraded(true);
                        answerRepository.save(ans);
                    }
                } else {
                    pendingManual = true;
                }
            } else if (ans != null && !blank(ans.getTextAnswer())) {
                if (!blank(q.getReferenceAnswer()) && textMatches(ans.getTextAnswer(), q.getReferenceAnswer())) {
                    ans.setObtainedMarks(q.getMarks());
                    ans.setAutoGraded(true);
                    answerRepository.save(ans);
                } else {
                    pendingManual = true;
                }
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
        Instant deadline = attempt.getDeadlineAt() != null ? attempt.getDeadlineAt() : examEnd(exam);
        if ("IN_PROGRESS".equals(attempt.getStatus())
                && (now.isAfter(deadline) || !now.isBefore(examEnd(exam)))) {
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
        if (!exam.getClassId().equals(me.getClassId())) {
            throw new ForbiddenException("This exam is not assigned to your class");
        }
        List<UUID> sections = sectionsOf(exam);
        if (me.getSectionId() == null || !sections.contains(me.getSectionId())) {
            throw new ForbiddenException("This exam is not assigned to your section");
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

    private void assertTeacherCanWrite(UUID classId, UUID subjectId, List<UUID> sections) {
        if (TenantContext.getRole() != UserRole.TEACHER) return;
        UUID teacherId = TenantContext.getUserId();
        for (UUID sectionId : sections) {
            academicService.assertTeacherAssigned(teacherId, classId, sectionId, subjectId);
        }
    }

    private void applyBody(OnlineExam exam, Map<String, Object> body) {
        if (body == null) return;
        if (body.containsKey("title")) exam.setTitle(str(body.get("title"), ""));
        if (body.containsKey("description")) exam.setDescription(str(body.get("description"), null));
        if (body.containsKey("classId")) exam.setClassId(uuid(body.get("classId")));
        if (body.containsKey("sectionId")) exam.setSectionId(uuid(body.get("sectionId")));
        if (body.containsKey("subjectId")) exam.setSubjectId(uuid(body.get("subjectId")));
        if (body.containsKey("campusId")) exam.setCampusId(uuid(body.get("campusId")));
        if (body.containsKey("startAt")) {
            try {
                exam.setStartAt(Instants.parse(str(body.get("startAt"), null)));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ex.getMessage());
            }
        }
        if (body.containsKey("durationMinutes")) {
            exam.setDurationMinutes(intVal(body.get("durationMinutes"), 0));
        }
        if (body.containsKey("passingMarks")) exam.setPassingMarks(decimal(body.get("passingMarks")));
        if (body.containsKey("mcqMarks")) exam.setMcqMarks(decimal(body.get("mcqMarks")));
        if (body.containsKey("shortMarks")) exam.setShortMarks(decimal(body.get("shortMarks")));
        if (body.containsKey("longMarks")) exam.setLongMarks(decimal(body.get("longMarks")));
        if (body.containsKey("shortRequired")) exam.setShortRequired(nullableInt(body.get("shortRequired")));
        if (body.containsKey("longRequired")) exam.setLongRequired(nullableInt(body.get("longRequired")));
        if (body.containsKey("examDate")) {
            String raw = str(body.get("examDate"), null);
            exam.setExamDate(raw == null ? null : LocalDate.parse(raw.substring(0, Math.min(10, raw.length()))));
        } else if (exam.getExamDate() == null && exam.getStartAt() != null) {
            exam.setExamDate(exam.getStartAt().atZone(KARACHI).toLocalDate());
        }
    }

    private List<UUID> resolveSections(UUID classId, Map<String, Object> body) {
        if (classId == null) {
            throw new BusinessException("Class is required");
        }
        academicService.requireClass(classId);
        List<Section> classSections = sectionRepository.findByTenantIdAndClassIdOrderByNameAsc(
                TenantGuard.requireTenantId(null), classId);
        boolean all = truthy(body == null ? null : body.get("allSections"));
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (all) {
            for (Section s : classSections) ids.add(s.getId());
        } else {
            if (body != null) {
                ids.addAll(uuidList(body.get("sectionIds")));
                UUID one = uuid(body.get("sectionId"));
                if (one != null) ids.add(one);
            }
        }
        if (ids.isEmpty()) {
            throw new BusinessException("Select at least one section, or All sections");
        }
        Set<UUID> allowed = classSections.stream().map(Section::getId).collect(Collectors.toSet());
        for (UUID sid : ids) {
            if (!allowed.contains(sid)) {
                Section s = academicService.requireSection(sid);
                if (!classId.equals(s.getClassId())) {
                    throw new BusinessException("Section does not belong to the selected class");
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private void replaceSections(OnlineExam exam, List<UUID> sections) {
        examSectionRepository.deleteAll(examSectionRepository.findByTenantIdAndExamId(exam.getTenantId(), exam.getId()));
        examSectionRepository.flush();
        for (UUID sid : sections) {
            OnlineExamSection row = new OnlineExamSection();
            row.setTenantId(exam.getTenantId());
            row.setExamId(exam.getId());
            row.setSectionId(sid);
            examSectionRepository.save(row);
        }
    }

    private List<UUID> sectionsOf(OnlineExam exam) {
        List<UUID> ids = examSectionRepository.findByTenantIdAndExamId(exam.getTenantId(), exam.getId())
                .stream().map(OnlineExamSection::getSectionId).toList();
        if (ids.isEmpty()) return fallbackSections(exam);
        return ids;
    }

    private List<UUID> fallbackSections(OnlineExam exam) {
        return exam.getSectionId() == null ? List.of() : List.of(exam.getSectionId());
    }

    private Map<UUID, List<UUID>> sectionMap(List<OnlineExam> exams) {
        if (exams == null || exams.isEmpty()) return Map.of();
        UUID tenantId = exams.get(0).getTenantId();
        List<UUID> ids = exams.stream().map(OnlineExam::getId).toList();
        Map<UUID, List<UUID>> out = new HashMap<>();
        for (OnlineExamSection row : examSectionRepository.findByTenantIdAndExamIdIn(tenantId, ids)) {
            out.computeIfAbsent(row.getExamId(), k -> new ArrayList<>()).add(row.getSectionId());
        }
        return out;
    }

    private void validateMeta(OnlineExam incoming, List<UUID> sections) {
        if (incoming.getTitle() == null || incoming.getTitle().isBlank()) {
            throw new BusinessException("Exam title is required");
        }
        if (incoming.getClassId() == null) {
            throw new BusinessException("Class is required");
        }
        academicService.requireClass(incoming.getClassId());
        if (incoming.getSubjectId() == null) {
            throw new BusinessException("Subject is required");
        }
        academicService.requireSubject(incoming.getSubjectId());
        if (sections == null || sections.isEmpty()) {
            throw new BusinessException("Select at least one section, or All sections");
        }
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

    private Map<String, Object> toStaffSummary(OnlineExam exam, List<UUID> sectionIds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", exam.getId());
        m.put("title", exam.getTitle());
        m.put("classId", exam.getClassId());
        m.put("sectionId", sectionIds.isEmpty() ? exam.getSectionId() : sectionIds.get(0));
        m.put("sectionIds", sectionIds);
        m.put("subjectId", exam.getSubjectId());
        m.put("startAt", exam.getStartAt());
        m.put("durationMinutes", exam.getDurationMinutes());
        m.put("totalMarks", exam.getTotalMarks());
        m.put("status", computeStatus(exam, exam.getStatus()));
        return m;
    }

    private Map<String, Object> toStaffExam(OnlineExam exam, List<OnlineExamQuestion> qs) {
        List<UUID> sectionIds = sectionsOf(exam);
        Map<String, Object> m = toStaffSummary(exam, sectionIds);
        m.put("description", exam.getDescription());
        m.put("examDate", exam.getExamDate());
        m.put("passingMarks", exam.getPassingMarks());
        m.put("mcqMarks", exam.getMcqMarks());
        m.put("shortMarks", exam.getShortMarks());
        m.put("longMarks", exam.getLongMarks());
        m.put("shortRequired", exam.getShortRequired());
        m.put("longRequired", exam.getLongRequired());
        m.put("campusId", exam.getCampusId());
        long attempts = attemptRepository.countByTenantIdAndExamId(exam.getTenantId(), exam.getId());
        m.put("attemptCount", attempts);
        boolean editable = "DRAFT".equals(exam.getStatus()) || "SCHEDULED".equals(exam.getStatus());
        m.put("assignmentLocked", attempts > 0 || !editable);
        int classSectionCount = sectionRepository.findByTenantIdAndClassIdOrderByNameAsc(
                exam.getTenantId(), exam.getClassId()).size();
        m.put("allSections", classSectionCount > 0 && sectionIds.size() == classSectionCount);
        m.put("questions", qs.stream().map(this::staffQuestion).toList());
        return m;
    }

    private Map<String, Object> staffQuestion(OnlineExamQuestion q) {
        Map<String, Object> m = studentQuestion(q);
        m.put("correctOption", q.getCorrectOption());
        m.put("referenceAnswer", q.getReferenceAnswer());
        m.put("hasAnswerKey", "MCQ".equals(q.getQuestionType())
                ? !blank(q.getCorrectOption())
                : !blank(q.getReferenceAnswer()));
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
        return toStudentSummary(exam, sectionsOf(exam));
    }

    private Map<String, Object> toStudentSummary(OnlineExam exam, List<UUID> sectionIds) {
        Instant now = Instant.now();
        Instant end = examEnd(exam);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", exam.getId());
        m.put("title", exam.getTitle());
        m.put("description", exam.getDescription());
        m.put("classId", exam.getClassId());
        m.put("sectionId", sectionIds.isEmpty() ? exam.getSectionId() : sectionIds.get(0));
        m.put("sectionIds", sectionIds);
        m.put("subjectId", exam.getSubjectId());
        m.put("startAt", exam.getStartAt());
        m.put("durationMinutes", exam.getDurationMinutes());
        m.put("totalMarks", exam.getTotalMarks());
        m.put("status", computeStatus(exam, exam.getStatus()));
        m.put("serverNow", now);
        m.put("deadlineAt", end);
        long remaining = ChronoUnit.SECONDS.between(now, end);
        m.put("remainingSeconds", remaining > 0 ? remaining : 0);
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
        Instant deadline = attempt.getDeadlineAt() != null ? attempt.getDeadlineAt() : examEnd(exam);
        m.put("deadlineAt", deadline);
        long remaining = ChronoUnit.SECONDS.between(now, deadline);
        m.put("remainingSeconds", remaining > 0 ? remaining : 0);
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
        boolean released = attachReleasedGrade(m, exam, attempt, questions(exam), answersOf(attempt));
        if (!released) {
            if ("SUBMITTED".equals(attempt.getStatus())) {
                m.put("message", "Submitted — awaiting teacher evaluation");
            } else {
                m.put("message", "Submitted — results not published yet");
            }
            m.put("totalMarks", attempt.getTotalMarks());
            return m;
        }
        if (withKeys) {
            m.put("questions", questions(exam).stream().map(this::staffQuestion).toList());
        }
        return m;
    }

    private boolean attachReleasedGrade(Map<String, Object> m,
                                       OnlineExam exam,
                                       OnlineExamAttempt attempt,
                                       List<OnlineExamQuestion> qs,
                                       Map<UUID, OnlineExamAnswer> answers) {
        boolean published = "RESULTS_PUBLISHED".equals(exam.getStatus());
        boolean auto = fullyAutoGraded(attempt, qs, answers);
        m.put("fullyAutoGraded", auto);
        if (!"GRADED".equals(attempt.getStatus())) {
            return false;
        }
        if (!auto && !published) {
            return false;
        }
        m.put("mcqObtained", attempt.getMcqObtained());
        m.put("shortObtained", attempt.getShortObtained());
        m.put("longObtained", attempt.getLongObtained());
        m.put("obtainedMarks", attempt.getObtainedMarks());
        m.put("totalMarks", attempt.getTotalMarks());
        m.put("percentage", attempt.getPercentage());
        m.put("grade", attempt.getGrade());
        m.put("passStatus", attempt.getPassStatus());
        return true;
    }

    private boolean fullyAutoGraded(OnlineExamAttempt attempt,
                                   List<OnlineExamQuestion> qs,
                                   Map<UUID, OnlineExamAnswer> answers) {
        if (!"GRADED".equals(attempt.getStatus())) return false;
        for (OnlineExamQuestion q : qs) {
            if ("MCQ".equals(q.getQuestionType())) {
                if (blank(q.getCorrectOption())) return false;
            } else {
                OnlineExamAnswer ans = answers.get(q.getId());
                if (ans != null && !blank(ans.getTextAnswer())) {
                    if (blank(q.getReferenceAnswer()) || !ans.isAutoGraded() || ans.getObtainedMarks() == null) {
                        return false;
                    }
                }
            }
        }
        return true;
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
        m.put("attemptStatus", attempt.getStatus());
        Map<UUID, OnlineExamAnswer> answers = answersOf(attempt);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> qs = (List<Map<String, Object>>) m.get("questions");
        for (Map<String, Object> q : qs) {
            UUID qid = (UUID) q.get("id");
            OnlineExamAnswer ans = answers.get(qid);
            String type = String.valueOf(q.get("questionType"));
            boolean hasKey = Boolean.TRUE.equals(q.get("hasAnswerKey"));
            if (ans != null) {
                q.put("obtainedMarks", ans.getObtainedMarks());
                q.put("autoGraded", ans.isAutoGraded());
            }
            String gradingStatus;
            if (ans != null && ans.getObtainedMarks() != null) {
                gradingStatus = ans.isAutoGraded() ? "AUTO" : "GRADED";
            } else if ("MCQ".equals(type) && hasKey) {
                gradingStatus = "AUTO";
            } else if (ans != null && (!blank(ans.getTextAnswer()) || !blank(ans.getOptionChoice()))) {
                gradingStatus = "PENDING";
            } else {
                gradingStatus = "UNANSWERED";
            }
            q.put("gradingStatus", gradingStatus);
        }
        return m;
    }

    private static boolean sameIds(List<UUID> a, List<UUID> b) {
        if (a.size() != b.size()) return false;
        return Set.copyOf(a).equals(Set.copyOf(b));
    }

    private static boolean textMatches(String given, String key) {
        return normalizeText(given).equals(normalizeText(key));
    }

    private static String normalizeText(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ").toLowerCase();
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
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<UUID> uuidList(Object v) {
        if (!(v instanceof List<?> list)) return List.of();
        List<UUID> out = new ArrayList<>();
        for (Object o : list) {
            UUID id = uuid(o);
            if (id != null) out.add(id);
        }
        return out;
    }

    private static boolean truthy(Object v) {
        if (v instanceof Boolean b) return b;
        if (v == null) return false;
        String s = v.toString().trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    private static int intVal(Object v, int d) {
        if (v == null || v.toString().isBlank()) return d;
        try {
            return (int) Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private static Integer nullableInt(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        try {
            return (int) Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
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
