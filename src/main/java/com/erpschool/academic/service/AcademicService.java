package com.erpschool.academic.service;

import com.erpschool.academic.entity.ClassSubject;
import com.erpschool.academic.entity.SchoolClass;
import com.erpschool.academic.entity.Section;
import com.erpschool.academic.entity.Subject;
import com.erpschool.academic.entity.TeacherAssignment;
import com.erpschool.academic.entity.TimetableSettings;
import com.erpschool.academic.entity.TimetableSlot;
import com.erpschool.academic.repository.ClassSubjectRepository;
import com.erpschool.academic.repository.SchoolClassRepository;
import com.erpschool.academic.repository.SectionRepository;
import com.erpschool.academic.repository.SubjectRepository;
import com.erpschool.academic.repository.TeacherAssignmentRepository;
import com.erpschool.academic.repository.TimetableSettingsRepository;
import com.erpschool.academic.repository.TimetableSlotRepository;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.DuplicateResourceException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AcademicService {

    private final SchoolClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final TeacherAssignmentRepository assignmentRepository;
    private final TimetableSlotRepository timetableRepository;
    private final TimetableSettingsRepository timetableSettingsRepository;
    private final UserRepository userRepository;

    public AcademicService(SchoolClassRepository classRepository,
                           SectionRepository sectionRepository,
                           SubjectRepository subjectRepository,
                           ClassSubjectRepository classSubjectRepository,
                           TeacherAssignmentRepository assignmentRepository,
                           TimetableSlotRepository timetableRepository,
                           TimetableSettingsRepository timetableSettingsRepository,
                           UserRepository userRepository) {
        this.classRepository = classRepository;
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.classSubjectRepository = classSubjectRepository;
        this.assignmentRepository = assignmentRepository;
        this.timetableRepository = timetableRepository;
        this.timetableSettingsRepository = timetableSettingsRepository;
        this.userRepository = userRepository;
    }

    private UUID tid() {
        return TenantGuard.requireTenantId(null);
    }

    private void stampNew(com.erpschool.common.entity.TenantAwareEntity e) {
        e.setTenantId(tid());
        e.setCreatedBy(TenantContext.getUserId());
    }

    @Transactional
    public SchoolClass createClass(SchoolClass c) {
        stampNew(c);
        return classRepository.save(c);
    }

    @Transactional(readOnly = true)
    public List<SchoolClass> classes() {
        return classRepository.findByTenantIdOrderByNameAsc(tid());
    }

    @Transactional
    public Section createSection(UUID classId, String name) {
        requireClass(classId);
        Section s = new Section();
        stampNew(s);
        s.setClassId(classId);
        s.setName(name.trim());
        return sectionRepository.save(s);
    }

    @Transactional(readOnly = true)
    public List<Section> sections(UUID classId) {
        return sectionRepository.findByTenantIdAndClassIdOrderByNameAsc(tid(), classId);
    }

    @Transactional(readOnly = true)
    public List<Section> allSections() {
        return sectionRepository.findByTenantId(tid());
    }

    @Transactional
    public Subject createSubject(String name, String code) {
        UUID tenantId = tid();
        if (subjectRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, code)) {
            throw new DuplicateResourceException("Subject code already exists");
        }
        Subject s = new Subject();
        stampNew(s);
        s.setName(name);
        s.setCode(code.trim().toUpperCase());
        return subjectRepository.save(s);
    }

    @Transactional(readOnly = true)
    public List<Subject> subjects() {
        return subjectRepository.findByTenantIdOrderByNameAsc(tid());
    }

    @Transactional
    public ClassSubject assignSubjectToClass(UUID classId, UUID subjectId) {
        requireClass(classId);
        requireSubject(subjectId);
        if (classSubjectRepository.existsByTenantIdAndClassIdAndSubjectId(tid(), classId, subjectId)) {
            throw new DuplicateResourceException("Subject already assigned to class");
        }
        ClassSubject cs = new ClassSubject();
        stampNew(cs);
        cs.setClassId(classId);
        cs.setSubjectId(subjectId);
        return classSubjectRepository.save(cs);
    }

    @Transactional
    public TeacherAssignment assignTeacher(UUID teacherUserId, UUID classId, UUID sectionId, UUID subjectId) {
        User teacher = userRepository.findById(teacherUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", teacherUserId));
        TenantGuard.assertSameTenant(teacher.getTenantId());
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new BusinessException("User is not a teacher");
        }
        requireClass(classId);
        requireSection(sectionId);
        requireSubject(subjectId);
        if (assignmentRepository.existsByTenantIdAndTeacherUserIdAndClassIdAndSectionIdAndSubjectId(
                tid(), teacherUserId, classId, sectionId, subjectId)) {
            throw new DuplicateResourceException("Teacher already assigned to this lecture");
        }
        TeacherAssignment a = new TeacherAssignment();
        stampNew(a);
        a.setTeacherUserId(teacherUserId);
        a.setClassId(classId);
        a.setSectionId(sectionId);
        a.setSubjectId(subjectId);
        return assignmentRepository.save(a);
    }

    @Transactional(readOnly = true)
    public List<TeacherAssignment> teacherAssignments(UUID teacherUserId) {
        UUID id = teacherUserId != null ? teacherUserId : TenantContext.getUserId();
        if (TenantContext.getRole() == UserRole.TEACHER) {
            id = TenantContext.getUserId();
        }
        return assignmentRepository.findByTenantIdAndTeacherUserId(tid(), id);
    }

    @Transactional
    public Map<String, Object> createSlot(TimetableSlot slot) {
        prepareSlot(slot, null);
        stampNew(slot);
        return describeSlot(timetableRepository.save(slot));
    }

    @Transactional
    public Map<String, Object> updateSlot(UUID id, TimetableSlot incoming) {
        TimetableSlot slot = requireSlot(id);
        slot.setClassId(incoming.getClassId());
        slot.setSectionId(incoming.getSectionId());
        slot.setSubjectId(incoming.getSubjectId());
        slot.setTeacherUserId(incoming.getTeacherUserId());
        slot.setDayOfWeek(incoming.getDayOfWeek());
        slot.setStartTime(incoming.getStartTime());
        slot.setEndTime(incoming.getEndTime());
        slot.setUpdatedBy(TenantContext.getUserId());
        prepareSlot(slot, slot.getId());
        return describeSlot(timetableRepository.save(slot));
    }

    @Transactional
    public void deleteSlot(UUID id) {
        TimetableSlot slot = requireSlot(id);
        timetableRepository.delete(slot);
    }

    @Transactional
    public List<Map<String, Object>> applyPattern(UUID classId, UUID sectionId, List<Integer> days,
                                                  List<TimetableSlot> lectures, boolean replace) {
        requireClass(classId);
        requireSection(sectionId);
        Set<Integer> working = workingDaySet();
        List<Integer> applyDays = (days == null || days.isEmpty() ? List.copyOf(working) : days).stream()
                .filter(working::contains)
                .distinct()
                .toList();
        if (applyDays.isEmpty()) {
            throw new BusinessException("Select at least one working day");
        }
        if (replace) {
            timetableRepository.deleteByTenantIdAndClassIdAndSectionIdAndDayOfWeekIn(
                    tid(), classId, sectionId, applyDays);
        }
        List<TimetableSlot> saved = new ArrayList<>();
        for (Integer day : applyDays) {
            for (TimetableSlot template : lectures) {
                TimetableSlot slot = new TimetableSlot();
                slot.setClassId(classId);
                slot.setSectionId(sectionId);
                slot.setSubjectId(template.getSubjectId());
                slot.setTeacherUserId(template.getTeacherUserId());
                slot.setDayOfWeek(day);
                slot.setStartTime(template.getStartTime());
                slot.setEndTime(template.getEndTime());
                prepareSlot(slot, null);
                stampNew(slot);
                saved.add(timetableRepository.save(slot));
            }
        }
        return describeSlots(saved);
    }

    @Transactional
    public List<Map<String, Object>> copyTimetable(UUID fromClassId, UUID fromSectionId,
                                                   UUID toClassId, UUID toSectionId, List<Integer> days) {
        List<TimetableSlot> source = timetableRepository
                .findByTenantIdAndClassIdAndSectionIdOrderByDayOfWeekAscStartTimeAsc(tid(), fromClassId, fromSectionId);
        if (days != null && !days.isEmpty()) {
            Set<Integer> allow = new HashSet<>(days);
            source = source.stream().filter(s -> allow.contains(s.getDayOfWeek())).toList();
        }
        return applyPattern(toClassId, toSectionId,
                source.stream().map(TimetableSlot::getDayOfWeek).distinct().toList(),
                source, true);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> classTimetable(UUID classId, UUID sectionId) {
        return describeSlots(timetableRepository.findByTenantIdAndClassIdAndSectionIdOrderByDayOfWeekAscStartTimeAsc(
                tid(), classId, sectionId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> teacherTimetable() {
        return describeSlots(timetableRepository.findByTenantIdAndTeacherUserIdOrderByDayOfWeekAscStartTimeAsc(
                tid(), TenantContext.getUserId()));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> teacherToday(int isoDay) {
        return describeSlots(timetableRepository.findByTenantIdAndTeacherUserIdAndDayOfWeek(
                tid(), TenantContext.getUserId(), isoDay));
    }

    @Transactional
    public TimetableSettings saveSettings(TimetableSettings incoming) {
        UUID tenantId = tid();
        TimetableSettings row = timetableSettingsRepository.findById(tenantId).orElseGet(TimetableSettings::new);
        if (row.getTenantId() == null) {
            row.setTenantId(tenantId);
            row.setCreatedBy(TenantContext.getUserId());
        }
        if (incoming.getStartTime() != null) row.setStartTime(incoming.getStartTime());
        if (incoming.getEndTime() != null) row.setEndTime(incoming.getEndTime());
        if (incoming.getLectureMinutes() > 0) row.setLectureMinutes(incoming.getLectureMinutes());
        if (incoming.getBreakMinutes() >= 0) row.setBreakMinutes(incoming.getBreakMinutes());
        if (incoming.getLecturesPerDay() > 0) row.setLecturesPerDay(incoming.getLecturesPerDay());
        if (incoming.getWorkingDays() != null && !incoming.getWorkingDays().isBlank()) {
            row.setWorkingDays(normalizeWorkingDays(incoming.getWorkingDays()));
        }
        row.setUpdatedBy(TenantContext.getUserId());
        row.setUpdatedAt(java.time.Instant.now());
        return timetableSettingsRepository.save(row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> settingsView() {
        TimetableSettings s = settings();
        Map<String, Object> m = new HashMap<>();
        m.put("startTime", s.getStartTime());
        m.put("endTime", s.getEndTime());
        m.put("lectureMinutes", s.getLectureMinutes());
        m.put("breakMinutes", s.getBreakMinutes());
        m.put("lecturesPerDay", s.getLecturesPerDay());
        m.put("workingDays", s.getWorkingDays());
        m.put("suggestedSlots", suggestedSlots(s));
        return m;
    }

    public SchoolClass requireClass(UUID id) {
        SchoolClass c = classRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Class", id));
        TenantGuard.assertSameTenant(c.getTenantId());
        return c;
    }

    public Section requireSection(UUID id) {
        Section s = sectionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Section", id));
        TenantGuard.assertSameTenant(s.getTenantId());
        return s;
    }

    public Subject requireSubject(UUID id) {
        Subject s = subjectRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subject", id));
        TenantGuard.assertSameTenant(s.getTenantId());
        return s;
    }

    public TimetableSlot requireSlot(UUID id) {
        TimetableSlot s = timetableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable slot", id));
        TenantGuard.assertSameTenant(s.getTenantId());
        return s;
    }

    public void assertTeacherAssigned(UUID teacherUserId, UUID classId, UUID sectionId, UUID subjectId) {
        if (!assignmentRepository.existsByTenantIdAndTeacherUserIdAndClassIdAndSectionIdAndSubjectId(
                tid(), teacherUserId, classId, sectionId, subjectId)) {
            throw new ForbiddenException("Teacher is not assigned to this class/section/subject");
        }
    }

    private void prepareSlot(TimetableSlot slot, UUID excludeId) {
        requireClass(slot.getClassId());
        Section section = requireSection(slot.getSectionId());
        if (!section.getClassId().equals(slot.getClassId())) {
            throw new BusinessException("Section does not belong to the selected class");
        }
        requireSubject(slot.getSubjectId());
        User teacher = userRepository.findById(slot.getTeacherUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", slot.getTeacherUserId()));
        TenantGuard.assertSameTenant(teacher.getTenantId());
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new BusinessException("User is not a teacher");
        }
        if (slot.getEndTime() == null || slot.getStartTime() == null
                || !slot.getEndTime().isAfter(slot.getStartTime())) {
            throw new BusinessException("End time must be after start time");
        }
        if (slot.getDayOfWeek() < 1 || slot.getDayOfWeek() > 7) {
            throw new BusinessException("dayOfWeek must be 1 (Monday) to 7 (Sunday)");
        }
        if (!workingDaySet().contains(slot.getDayOfWeek())) {
            throw new BusinessException("That day is not a working day for this school");
        }
        for (TimetableSlot existing : timetableRepository.findByTenantIdAndTeacherUserIdAndDayOfWeek(
                tid(), slot.getTeacherUserId(), slot.getDayOfWeek())) {
            if (excludeId != null && excludeId.equals(existing.getId())) {
                continue;
            }
            if (overlaps(slot.getStartTime(), slot.getEndTime(), existing.getStartTime(), existing.getEndTime())) {
                throw new BusinessException("Teacher is already assigned to another class during this time.");
            }
        }
        for (TimetableSlot existing : timetableRepository.findByTenantIdAndClassIdAndSectionIdAndDayOfWeek(
                tid(), slot.getClassId(), slot.getSectionId(), slot.getDayOfWeek())) {
            if (excludeId != null && excludeId.equals(existing.getId())) {
                continue;
            }
            if (overlaps(slot.getStartTime(), slot.getEndTime(), existing.getStartTime(), existing.getEndTime())) {
                throw new BusinessException("This class/section already has a lecture during this time.");
            }
        }
    }

    private static boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    private TimetableSettings settings() {
        return timetableSettingsRepository.findById(tid()).orElseGet(() -> {
            TimetableSettings s = new TimetableSettings();
            s.setTenantId(tid());
            return s;
        });
    }

    private Set<Integer> workingDaySet() {
        Set<Integer> days = new HashSet<>();
        for (String part : settings().getWorkingDays().split(",")) {
            try {
                int d = Integer.parseInt(part.trim());
                if (d >= 1 && d <= 7) {
                    days.add(d);
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        if (days.isEmpty()) {
            days.addAll(List.of(1, 2, 3, 4, 5));
        }
        return days;
    }

    private String normalizeWorkingDays(String raw) {
        Set<Integer> days = new HashSet<>();
        for (String part : raw.split(",")) {
            try {
                int d = Integer.parseInt(part.trim());
                if (d >= 1 && d <= 7) {
                    days.add(d);
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        if (days.isEmpty()) {
            throw new BusinessException("Select at least one working day (1=Monday … 7=Sunday)");
        }
        return days.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Map<String, Object>> suggestedSlots(TimetableSettings s) {
        List<Map<String, Object>> rows = new ArrayList<>();
        LocalTime cursor = s.getStartTime();
        for (int i = 1; i <= s.getLecturesPerDay(); i++) {
            LocalTime end = cursor.plusMinutes(s.getLectureMinutes());
            if (s.getEndTime() != null && end.isAfter(s.getEndTime())) {
                break;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("lectureNo", i);
            row.put("startTime", cursor);
            row.put("endTime", end);
            rows.add(row);
            cursor = end.plusMinutes(s.getBreakMinutes());
        }
        return rows;
    }

    private List<Map<String, Object>> describeSlots(List<TimetableSlot> slots) {
        if (slots.isEmpty()) {
            return List.of();
        }
        Set<UUID> classIds = new HashSet<>();
        Set<UUID> sectionIds = new HashSet<>();
        Set<UUID> subjectIds = new HashSet<>();
        Set<UUID> teacherIds = new HashSet<>();
        for (TimetableSlot slot : slots) {
            classIds.add(slot.getClassId());
            sectionIds.add(slot.getSectionId());
            subjectIds.add(slot.getSubjectId());
            teacherIds.add(slot.getTeacherUserId());
        }
        Map<UUID, SchoolClass> classes = classRepository.findAllById(classIds).stream()
                .collect(Collectors.toMap(SchoolClass::getId, c -> c));
        Map<UUID, Section> sections = sectionRepository.findAllById(sectionIds).stream()
                .collect(Collectors.toMap(Section::getId, s -> s));
        Map<UUID, Subject> subjects = subjectRepository.findAllById(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, s -> s));
        Map<UUID, User> teachers = userRepository.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> out = new ArrayList<>();
        for (TimetableSlot slot : slots) {
            Map<String, Object> m = describeSlotBare(slot);
            SchoolClass c = classes.get(slot.getClassId());
            Section sec = sections.get(slot.getSectionId());
            Subject sub = subjects.get(slot.getSubjectId());
            User t = teachers.get(slot.getTeacherUserId());
            m.put("className", c == null ? null : c.getName());
            m.put("sectionName", sec == null ? null : sec.getName());
            m.put("subjectName", sub == null ? null : sub.getName());
            m.put("teacherName", t == null ? null : t.getFullName());
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> describeSlot(TimetableSlot slot) {
        return describeSlots(List.of(slot)).get(0);
    }

    private static Map<String, Object> describeSlotBare(TimetableSlot slot) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", slot.getId());
        m.put("classId", slot.getClassId());
        m.put("sectionId", slot.getSectionId());
        m.put("subjectId", slot.getSubjectId());
        m.put("teacherUserId", slot.getTeacherUserId());
        m.put("dayOfWeek", slot.getDayOfWeek());
        m.put("startTime", slot.getStartTime());
        m.put("endTime", slot.getEndTime());
        return m;
    }
}
