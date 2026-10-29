package com.erpschool.exam.service;

import com.erpschool.academic.entity.SchoolClass;
import com.erpschool.academic.entity.Section;
import com.erpschool.academic.entity.Subject;
import com.erpschool.academic.entity.TeacherAssignment;
import com.erpschool.academic.repository.ClassSubjectRepository;
import com.erpschool.academic.repository.SchoolClassRepository;
import com.erpschool.academic.repository.SectionRepository;
import com.erpschool.academic.repository.SubjectRepository;
import com.erpschool.academic.repository.TeacherAssignmentRepository;
import com.erpschool.academic.service.AcademicService;
import com.erpschool.audit.service.AuditService;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.exam.entity.ExamDateSheet;
import com.erpschool.exam.entity.ExamDateSheetEntry;
import com.erpschool.exam.entity.ExamSession;
import com.erpschool.exam.online.OnlineExam;
import com.erpschool.exam.online.OnlineExamRepository;
import com.erpschool.exam.repository.ExamDateSheetHeaderRepository;
import com.erpschool.exam.repository.ExamDateSheetRepository;
import com.erpschool.exam.repository.ExamSessionRepository;
import com.erpschool.student.entity.Student;
import com.erpschool.student.repository.ParentStudentRepository;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.tenant.context.CampusScope;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.user.entity.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExamDateSheetService {

    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Karachi");
    private static final String THEORY = "THEORY";
    private static final String ONLINE = "ONLINE";

    private final ExamDateSheetHeaderRepository headerRepository;
    private final ExamDateSheetRepository entryRepository;
    private final ExamSessionRepository sessionRepository;
    private final AcademicService academicService;
    private final ClassSubjectRepository classSubjectRepository;
    private final SchoolClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final OnlineExamRepository onlineExamRepository;
    private final TeacherAssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final AuditService auditService;

    public ExamDateSheetService(ExamDateSheetHeaderRepository headerRepository,
                                ExamDateSheetRepository entryRepository,
                                ExamSessionRepository sessionRepository,
                                AcademicService academicService,
                                ClassSubjectRepository classSubjectRepository,
                                SchoolClassRepository classRepository,
                                SectionRepository sectionRepository,
                                SubjectRepository subjectRepository,
                                OnlineExamRepository onlineExamRepository,
                                TeacherAssignmentRepository assignmentRepository,
                                StudentRepository studentRepository,
                                ParentStudentRepository parentStudentRepository,
                                AuditService auditService) {
        this.headerRepository = headerRepository;
        this.entryRepository = entryRepository;
        this.sessionRepository = sessionRepository;
        this.academicService = academicService;
        this.classSubjectRepository = classSubjectRepository;
        this.classRepository = classRepository;
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.onlineExamRepository = onlineExamRepository;
        this.assignmentRepository = assignmentRepository;
        this.studentRepository = studentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID sessionId, UUID classId, UUID sectionId, Boolean published, String academicSession) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        List<ExamDateSheet> sheets = headerRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);
        Set<UUID> allowedClasses = visibleClassIds(tenantId);
        boolean manager = canManage();
        List<ExamDateSheet> filtered = new ArrayList<>();
        for (ExamDateSheet sheet : sheets) {
            if (sessionId != null && !sessionId.equals(sheet.getExamSessionId())) continue;
            if (classId != null && !classId.equals(sheet.getClassId())) continue;
            if (published != null && published != sheet.isPublished()) continue;
            if (!manager && !sheet.isPublished()) continue;
            if (allowedClasses != null && !allowedClasses.contains(sheet.getClassId())) continue;
            if (CampusScope.restricts() && sheet.getCampusId() != null
                    && !sheet.getCampusId().equals(CampusScope.current())) {
                continue;
            }
            filtered.add(sheet);
        }
        Map<UUID, ExamSession> sessions = loadSessions(filtered);
        if (academicSession != null && !academicSession.isBlank()) {
            String want = academicSession.trim();
            filtered = filtered.stream()
                    .filter(s -> {
                        ExamSession ex = sessions.get(s.getExamSessionId());
                        return ex != null && want.equalsIgnoreCase(ex.getAcademicSession());
                    })
                    .toList();
        }
        Map<UUID, List<ExamDateSheetEntry>> entriesBySheet = new HashMap<>();
        for (ExamDateSheet sheet : filtered) {
            List<ExamDateSheetEntry> rows = entryRepository
                    .findByTenantIdAndDateSheetIdOrderByExamDateAscStartTimeAsc(tenantId, sheet.getId());
            if (sectionId != null) {
                rows = rows.stream()
                        .filter(r -> r.getSectionId() == null || sectionId.equals(r.getSectionId()))
                        .toList();
            }
            entriesBySheet.put(sheet.getId(), rows);
        }
        return viewMany(filtered, sessions, entriesBySheet);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        ExamDateSheet sheet = requireSheet(id);
        assertCanView(sheet);
        UUID tenantId = sheet.getTenantId();
        List<ExamDateSheetEntry> rows = entryRepository
                .findByTenantIdAndDateSheetIdOrderByExamDateAscStartTimeAsc(tenantId, sheet.getId());
        return viewOne(sheet, requireSession(sheet.getExamSessionId()), rows);
    }

    @Transactional
    public Map<String, Object> save(UUID id, SaveRequest request) {
        assertCanManage();
        if (request == null || request.examSessionId == null || request.classId == null) {
            throw new BusinessException("DATE_SHEET_REQUIRED", "Select an exam session and class.");
        }
        ExamSession session = requireSession(request.examSessionId);
        SchoolClass schoolClass = academicService.requireClass(request.classId);
        assertCampus(schoolClass);
        UUID tenantId = session.getTenantId();
        ExamDateSheet sheet;
        if (id != null) {
            sheet = requireSheet(id);
            if (!sheet.getExamSessionId().equals(session.getId()) || !sheet.getClassId().equals(schoolClass.getId())) {
                throw new BusinessException("DATE_SHEET_LOCKED", "Exam session and class cannot be changed.");
            }
        } else {
            sheet = headerRepository.findByTenantIdAndExamSessionIdAndClassId(tenantId, session.getId(), schoolClass.getId())
                    .orElseGet(ExamDateSheet::new);
        }
        if (sheet.getId() != null) {
            assertWritable(sheet, session);
        } else if (isHistorical(session)) {
            throw new BusinessException("DATE_SHEET_HISTORICAL",
                    "This exam period is closed. Past date sheets cannot be created or changed.");
        }
        sheet.setTenantId(tenantId);
        sheet.setExamSessionId(session.getId());
        sheet.setClassId(schoolClass.getId());
        sheet.setCampusId(schoolClass.getCampusId() != null ? schoolClass.getCampusId() : session.getCampusId());
        sheet.setNotes(blankToNull(request.notes));
        if (sheet.getId() == null) {
            sheet.setCreatedBy(TenantContext.getUserId());
        }
        sheet.setUpdatedBy(TenantContext.getUserId());
        sheet = headerRepository.save(sheet);

        List<ExamDateSheetEntry> expanded = expandAndValidate(tenantId, session, schoolClass, sheet, request.entries);
        entryRepository.deleteByTenantIdAndDateSheetId(tenantId, sheet.getId());
        List<ExamDateSheetEntry> saved = new ArrayList<>();
        for (ExamDateSheetEntry row : expanded) {
            row.setPublished(sheet.isPublished());
            saved.add(entryRepository.save(row));
        }
        auditService.record("DATE_SHEET_SAVED", "ExamDateSheet", sheet.getId().toString(),
                Map.of("examSessionId", session.getId().toString(), "classId", schoolClass.getId().toString(),
                        "entries", saved.size()));
        return viewOne(sheet, session, saved);
    }

    @Transactional
    public Map<String, Object> publish(UUID id, boolean publish) {
        assertCanManage();
        ExamDateSheet sheet = requireSheet(id);
        ExamSession session = requireSession(sheet.getExamSessionId());
        if (isHistorical(session) && sheet.isPublished() && !publish) {
            throw new BusinessException("DATE_SHEET_HISTORICAL",
                    "Published results or a past exam period cannot be unpublished.");
        }
        if (isHistorical(session) && !sheet.isPublished() && publish && session.isPublished()) {
            throw new BusinessException("DATE_SHEET_HISTORICAL",
                    "This exam period is closed. The date sheet cannot be changed.");
        }
        List<ExamDateSheetEntry> rows = entryRepository
                .findByTenantIdAndDateSheetIdOrderByExamDateAscStartTimeAsc(sheet.getTenantId(), sheet.getId());
        if (publish && rows.isEmpty()) {
            throw new BusinessException("DATE_SHEET_EMPTY", "Add at least one subject schedule before publishing.");
        }
        sheet.setPublished(publish);
        sheet.setPublishedAt(publish ? Instant.now() : null);
        sheet.setUpdatedBy(TenantContext.getUserId());
        headerRepository.save(sheet);
        for (ExamDateSheetEntry row : rows) {
            row.setPublished(publish);
            entryRepository.save(row);
        }
        auditService.record(publish ? "DATE_SHEET_PUBLISHED" : "DATE_SHEET_UNPUBLISHED",
                "ExamDateSheet", sheet.getId().toString(),
                Map.of("examSessionId", session.getId().toString(), "classId", sheet.getClassId().toString()));
        return viewOne(sheet, session, rows);
    }

    @Transactional
    public void delete(UUID id) {
        assertCanManage();
        ExamDateSheet sheet = requireSheet(id);
        ExamSession session = requireSession(sheet.getExamSessionId());
        if (sheet.isPublished() || isHistorical(session)) {
            throw new BusinessException("DATE_SHEET_LOCKED",
                    "Unpublish a draft before deleting. Historical date sheets cannot be removed.");
        }
        entryRepository.deleteByTenantIdAndDateSheetId(sheet.getTenantId(), sheet.getId());
        headerRepository.delete(sheet);
        auditService.record("DATE_SHEET_DELETED", "ExamDateSheet", id.toString(),
                Map.of("examSessionId", session.getId().toString(), "classId", sheet.getClassId().toString()));
    }

    @Transactional
    public void deleteForSession(UUID tenantId, UUID sessionId) {
        headerRepository.deleteByTenantIdAndExamSessionId(tenantId, sessionId);
        entryRepository.deleteByTenantIdAndExamSessionId(tenantId, sessionId);
    }

    @Transactional
    public List<ExamDateSheetEntry> replaceSessionEntries(UUID sessionId, List<ExamDateSheetEntry> entries) {
        assertCanManage();
        ExamSession session = requireSession(sessionId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ExamDateSheetEntry>> byClass = new LinkedHashMap<>();
        for (ExamDateSheetEntry incoming : entries) {
            if (incoming.getClassId() == null) {
                throw new BusinessException("DATE_SHEET_CLASS", "Each schedule row needs a class.");
            }
            byClass.computeIfAbsent(incoming.getClassId(), k -> new ArrayList<>()).add(incoming);
        }
        List<ExamDateSheetEntry> saved = new ArrayList<>();
        for (Map.Entry<UUID, List<ExamDateSheetEntry>> group : byClass.entrySet()) {
            SaveRequest req = new SaveRequest();
            req.examSessionId = session.getId();
            req.classId = group.getKey();
            req.entries = new ArrayList<>();
            for (ExamDateSheetEntry row : group.getValue()) {
                EntryRequest er = new EntryRequest();
                er.sectionId = row.getSectionId();
                er.subjectId = row.getSubjectId();
                er.examDate = row.getExamDate();
                er.startTime = row.getStartTime();
                er.endTime = row.getEndTime();
                er.examType = row.getExamType();
                er.notes = row.getNotes();
                er.onlineExamId = row.getOnlineExamId();
                req.entries.add(er);
            }
            Map<String, Object> view = save(null, req);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> viewRows = (List<Map<String, Object>>) view.get("entries");
            if (viewRows == null) continue;
            UUID sheetId = UUID.fromString(String.valueOf(view.get("id")));
            saved.addAll(entryRepository.findByTenantIdAndDateSheetIdOrderByExamDateAscStartTimeAsc(
                    session.getTenantId(), sheetId));
        }
        return saved;
    }

    static boolean timesOverlap(LocalTime a0, LocalTime a1, LocalTime b0, LocalTime b1) {
        if (a0 == null || a1 == null || b0 == null || b1 == null) {
            return false;
        }
        return a0.isBefore(a1) && b0.isBefore(b1) && a0.isBefore(b1) && b0.isBefore(a1);
    }

    private List<ExamDateSheetEntry> expandAndValidate(UUID tenantId, ExamSession session, SchoolClass schoolClass,
                                                       ExamDateSheet sheet, List<EntryRequest> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return List.of();
        }
        Set<UUID> classSubjects = classSubjectRepository.findByTenantIdAndClassId(tenantId, schoolClass.getId())
                .stream().map(cs -> cs.getSubjectId()).collect(Collectors.toSet());
        List<ExamDateSheetEntry> expanded = new ArrayList<>();
        for (EntryRequest raw : incoming) {
            if (raw == null || raw.subjectId == null || raw.examDate == null) {
                throw new BusinessException("DATE_SHEET_ROW", "Each schedule needs a subject and a date.");
            }
            academicService.requireSubject(raw.subjectId);
            if (!classSubjects.contains(raw.subjectId)) {
                throw new BusinessException("DATE_SHEET_SUBJECT",
                        classSubjects.isEmpty()
                                ? "Assign subjects to this class under Academics before scheduling them."
                                : "That subject is not assigned to the selected class.");
            }
            if (raw.startTime != null && raw.endTime != null && !raw.endTime.isAfter(raw.startTime)) {
                throw new BusinessException("DATE_SHEET_TIME", "End time must be later than start time.");
            }
            String type = normalizeType(raw.examType);
            UUID onlineId = null;
            if (ONLINE.equals(type)) {
                if (raw.onlineExamId == null) {
                    throw new BusinessException("DATE_SHEET_ONLINE",
                            "Link an existing online exam for this class and subject, or create one under Online exams first. Saving a date sheet does not create or start an online exam.");
                }
                OnlineExam online = onlineExamRepository.findByIdAndTenantId(raw.onlineExamId, tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Online exam", raw.onlineExamId));
                if (!schoolClass.getId().equals(online.getClassId()) || !raw.subjectId.equals(online.getSubjectId())) {
                    throw new BusinessException("DATE_SHEET_ONLINE",
                            "The linked online exam must match this class and subject.");
                }
                onlineId = online.getId();
            }
            List<UUID> sectionIds = new ArrayList<>();
            if (raw.sectionIds != null) {
                for (UUID sid : raw.sectionIds) {
                    if (sid != null) sectionIds.add(sid);
                }
            }
            if (raw.sectionId != null && !sectionIds.contains(raw.sectionId)) {
                sectionIds.add(raw.sectionId);
            }
            if (sectionIds.isEmpty()) {
                expanded.add(newEntry(tenantId, session, schoolClass, sheet, raw, type, onlineId, null));
            } else {
                for (UUID sectionId : sectionIds) {
                    Section section = academicService.requireSection(sectionId);
                    if (!schoolClass.getId().equals(section.getClassId())) {
                        throw new BusinessException("DATE_SHEET_SECTION", "Section does not belong to the selected class.");
                    }
                    expanded.add(newEntry(tenantId, session, schoolClass, sheet, raw, type, onlineId, sectionId));
                }
            }
        }
        Set<String> dup = new HashSet<>();
        for (int i = 0; i < expanded.size(); i++) {
            ExamDateSheetEntry a = expanded.get(i);
            String key = a.getSubjectId() + "|" + String.valueOf(a.getSectionId()) + "|" + a.getExamDate()
                    + "|" + String.valueOf(a.getStartTime());
            if (!dup.add(key)) {
                throw new BusinessException("DATE_SHEET_DUPLICATE",
                        "The same subject is already scheduled for this class/section at that date and time.");
            }
            for (int j = i + 1; j < expanded.size(); j++) {
                ExamDateSheetEntry b = expanded.get(j);
                if (!a.getExamDate().equals(b.getExamDate())) continue;
                if (!sameSectionScope(a.getSectionId(), b.getSectionId())) continue;
                if (timesOverlap(a.getStartTime(), a.getEndTime(), b.getStartTime(), b.getEndTime())) {
                    throw new BusinessException("DATE_SHEET_OVERLAP",
                            "Two exams overlap for the same class/section on " + a.getExamDate() + ".");
                }
            }
        }
        return expanded;
    }

    private ExamDateSheetEntry newEntry(UUID tenantId, ExamSession session, SchoolClass schoolClass, ExamDateSheet sheet,
                                        EntryRequest raw, String type, UUID onlineId, UUID sectionId) {
        ExamDateSheetEntry row = new ExamDateSheetEntry();
        row.setTenantId(tenantId);
        row.setDateSheetId(sheet.getId());
        row.setExamSessionId(session.getId());
        row.setCampusId(sheet.getCampusId());
        row.setClassId(schoolClass.getId());
        row.setSectionId(sectionId);
        row.setSubjectId(raw.subjectId);
        row.setExamDate(raw.examDate);
        row.setStartTime(raw.startTime);
        row.setEndTime(raw.endTime);
        row.setExamType(type);
        row.setNotes(blankToNull(raw.notes));
        row.setOnlineExamId(onlineId);
        row.setCreatedBy(TenantContext.getUserId());
        return row;
    }

    private List<Map<String, Object>> viewMany(List<ExamDateSheet> sheets, Map<UUID, ExamSession> sessions,
                                               Map<UUID, List<ExamDateSheetEntry>> entriesBySheet) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ExamDateSheet sheet : sheets) {
            out.add(viewOne(sheet, sessions.get(sheet.getExamSessionId()),
                    entriesBySheet.getOrDefault(sheet.getId(), List.of())));
        }
        return out;
    }

    private Map<String, Object> viewOne(ExamDateSheet sheet, ExamSession session, List<ExamDateSheetEntry> rows) {
        SchoolClass schoolClass = classRepository.findById(sheet.getClassId()).orElse(null);
        Map<UUID, String> subjectNames = new HashMap<>();
        Map<UUID, String> sectionNames = new HashMap<>();
        Map<UUID, String> onlineTitles = new HashMap<>();
        Set<UUID> subjectIds = new HashSet<>();
        Set<UUID> sectionIds = new HashSet<>();
        Set<UUID> onlineIds = new HashSet<>();
        for (ExamDateSheetEntry row : rows) {
            subjectIds.add(row.getSubjectId());
            if (row.getSectionId() != null) sectionIds.add(row.getSectionId());
            if (row.getOnlineExamId() != null) onlineIds.add(row.getOnlineExamId());
        }
        if (!subjectIds.isEmpty()) {
            for (Subject s : subjectRepository.findAllById(subjectIds)) {
                subjectNames.put(s.getId(), s.getName());
            }
        }
        if (!sectionIds.isEmpty()) {
            for (Section s : sectionRepository.findAllById(sectionIds)) {
                sectionNames.put(s.getId(), s.getName());
            }
        }
        if (!onlineIds.isEmpty()) {
            for (OnlineExam e : onlineExamRepository.findAllById(onlineIds)) {
                onlineTitles.put(e.getId(), e.getTitle());
            }
        }
        List<Map<String, Object>> entryViews = new ArrayList<>();
        for (ExamDateSheetEntry row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row.getId());
            m.put("sectionId", row.getSectionId());
            m.put("sectionName", row.getSectionId() == null ? "All sections" : sectionNames.get(row.getSectionId()));
            m.put("subjectId", row.getSubjectId());
            m.put("subjectName", subjectNames.get(row.getSubjectId()));
            m.put("examDate", row.getExamDate());
            m.put("startTime", row.getStartTime());
            m.put("endTime", row.getEndTime());
            m.put("examType", row.getExamType() == null ? THEORY : row.getExamType());
            m.put("notes", row.getNotes());
            m.put("onlineExamId", row.getOnlineExamId());
            m.put("onlineExamTitle", row.getOnlineExamId() == null ? null : onlineTitles.get(row.getOnlineExamId()));
            entryViews.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", sheet.getId());
        out.put("examSessionId", sheet.getExamSessionId());
        out.put("examName", session == null ? null : session.getName());
        out.put("academicSession", session == null ? null : session.getAcademicSession());
        out.put("classId", sheet.getClassId());
        out.put("className", schoolClass == null ? null : schoolClass.getName());
        out.put("campusId", sheet.getCampusId());
        out.put("published", sheet.isPublished());
        out.put("publishedAt", sheet.getPublishedAt());
        out.put("historical", session != null && isHistorical(session));
        out.put("notes", sheet.getNotes());
        out.put("entries", entryViews);
        out.put("updatedAt", sheet.getUpdatedAt());
        return out;
    }

    private Map<UUID, ExamSession> loadSessions(List<ExamDateSheet> sheets) {
        Set<UUID> ids = sheets.stream().map(ExamDateSheet::getExamSessionId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        Map<UUID, ExamSession> map = new HashMap<>();
        for (ExamSession s : sessionRepository.findAllById(ids)) {
            map.put(s.getId(), s);
        }
        return map;
    }

    private ExamDateSheet requireSheet(UUID id) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        ExamDateSheet sheet = headerRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Date sheet", id));
        TenantGuard.assertSameTenant(sheet.getTenantId());
        return sheet;
    }

    private ExamSession requireSession(UUID id) {
        ExamSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam session", id));
        TenantGuard.assertSameTenant(session.getTenantId());
        return session;
    }

    private void assertCanView(ExamDateSheet sheet) {
        if (canManage()) {
            if (CampusScope.restricts() && sheet.getCampusId() != null
                    && !sheet.getCampusId().equals(CampusScope.current())) {
                throw new ForbiddenException("This date sheet belongs to another campus");
            }
            return;
        }
        if (!sheet.isPublished()) {
            throw new ForbiddenException("This date sheet is not published");
        }
        Set<UUID> allowed = visibleClassIds(sheet.getTenantId());
        if (allowed != null && !allowed.contains(sheet.getClassId())) {
            throw new ForbiddenException("You cannot view this date sheet");
        }
    }

    private void assertWritable(ExamDateSheet sheet, ExamSession session) {
        if (isHistorical(session)) {
            throw new BusinessException("DATE_SHEET_HISTORICAL",
                    "This exam period is closed. Past date sheets cannot be modified.");
        }
        if (sheet.isPublished()) {
            throw new BusinessException("DATE_SHEET_PUBLISHED",
                    "Unpublish the date sheet before editing schedules.");
        }
    }

    private void assertCampus(SchoolClass schoolClass) {
        if (!CampusScope.restricts()) return;
        UUID campusId = CampusScope.current();
        if (campusId != null && schoolClass.getCampusId() != null && !campusId.equals(schoolClass.getCampusId())) {
            throw new ForbiddenException("This class belongs to another campus");
        }
    }

    private boolean isHistorical(ExamSession session) {
        if (session.isPublished()) return true;
        if (session.getEndDate() == null) return false;
        return session.getEndDate().isBefore(LocalDate.now(SCHOOL_ZONE));
    }

    private boolean canManage() {
        UserRole role = TenantContext.getRole();
        return role == UserRole.ERP_OWNER || role == UserRole.SCHOOL_ADMIN || role == UserRole.PRINCIPAL;
    }

    private void assertCanManage() {
        if (!canManage()) {
            throw new ForbiddenException("You cannot create or change date sheets");
        }
    }

    /**
     * null = all classes (managers). Empty set = none visible.
     */
    private Set<UUID> visibleClassIds(UUID tenantId) {
        UserRole role = TenantContext.getRole();
        if (role == UserRole.ERP_OWNER || role == UserRole.SCHOOL_ADMIN || role == UserRole.PRINCIPAL) {
            return null;
        }
        Set<UUID> ids = new HashSet<>();
        if (role == UserRole.TEACHER) {
            for (TeacherAssignment a : assignmentRepository.findByTenantIdAndTeacherUserId(tenantId, TenantContext.getUserId())) {
                ids.add(a.getClassId());
            }
            return ids;
        }
        if (role == UserRole.STUDENT) {
            studentRepository.findByTenantIdAndUserId(tenantId, TenantContext.getUserId())
                    .ifPresent(s -> {
                        if (s.getClassId() != null) ids.add(s.getClassId());
                    });
            return ids;
        }
        if (role == UserRole.PARENT) {
            List<UUID> childIds = parentStudentRepository
                    .findByTenantIdAndParentUserId(tenantId, TenantContext.getUserId())
                    .stream().map(ps -> ps.getStudentId()).toList();
            if (!childIds.isEmpty()) {
                for (Student s : studentRepository.findAllById(childIds)) {
                    if (s.getClassId() != null) ids.add(s.getClassId());
                }
            }
            return ids;
        }
        return Set.of();
    }

    private static boolean sameSectionScope(UUID a, UUID b) {
        if (a == null || b == null) return true;
        return a.equals(b);
    }

    private static String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) return THEORY;
        String t = raw.trim().toUpperCase();
        if (THEORY.equals(t) || ONLINE.equals(t)) return t;
        throw new BusinessException("DATE_SHEET_TYPE", "Examination type must be Theory or Online.");
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @lombok.Getter
    @lombok.Setter
    public static class SaveRequest {
        private UUID examSessionId;
        private UUID classId;
        private String notes;
        private List<EntryRequest> entries;
    }

    @lombok.Getter
    @lombok.Setter
    public static class EntryRequest {
        private UUID sectionId;
        private List<UUID> sectionIds;
        private UUID subjectId;
        private LocalDate examDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private String examType;
        private String notes;
        private UUID onlineExamId;
    }
}
