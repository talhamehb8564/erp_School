package com.erpschool.academic.service;

import com.erpschool.academic.entity.ClassSubject;
import com.erpschool.academic.entity.SchoolClass;
import com.erpschool.academic.entity.Section;
import com.erpschool.academic.entity.Subject;
import com.erpschool.academic.entity.TeacherAssignment;
import com.erpschool.academic.entity.TimetableSlot;
import com.erpschool.academic.repository.ClassSubjectRepository;
import com.erpschool.academic.repository.SchoolClassRepository;
import com.erpschool.academic.repository.SectionRepository;
import com.erpschool.academic.repository.SubjectRepository;
import com.erpschool.academic.repository.TeacherAssignmentRepository;
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

import java.util.List;
import java.util.UUID;

@Service
public class AcademicService {

    private final SchoolClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final TeacherAssignmentRepository assignmentRepository;
    private final TimetableSlotRepository timetableRepository;
    private final UserRepository userRepository;

    public AcademicService(SchoolClassRepository classRepository,
                           SectionRepository sectionRepository,
                           SubjectRepository subjectRepository,
                           ClassSubjectRepository classSubjectRepository,
                           TeacherAssignmentRepository assignmentRepository,
                           TimetableSlotRepository timetableRepository,
                           UserRepository userRepository) {
        this.classRepository = classRepository;
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.classSubjectRepository = classSubjectRepository;
        this.assignmentRepository = assignmentRepository;
        this.timetableRepository = timetableRepository;
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
    public TimetableSlot createSlot(TimetableSlot slot) {
        requireClass(slot.getClassId());
        requireSection(slot.getSectionId());
        requireSubject(slot.getSubjectId());
        User teacher = userRepository.findById(slot.getTeacherUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", slot.getTeacherUserId()));
        TenantGuard.assertSameTenant(teacher.getTenantId());
        if (slot.getEndTime() == null || slot.getStartTime() == null
                || !slot.getEndTime().isAfter(slot.getStartTime())) {
            throw new BusinessException("End time must be after start time");
        }
        if (slot.getDayOfWeek() < 1 || slot.getDayOfWeek() > 7) {
            throw new BusinessException("dayOfWeek must be 1 (Monday) to 7 (Sunday)");
        }
        stampNew(slot);
        return timetableRepository.save(slot);
    }

    @Transactional(readOnly = true)
    public List<TimetableSlot> classTimetable(UUID classId, UUID sectionId) {
        return timetableRepository.findByTenantIdAndClassIdAndSectionIdOrderByDayOfWeekAscStartTimeAsc(
                tid(), classId, sectionId);
    }

    @Transactional(readOnly = true)
    public List<TimetableSlot> teacherTimetable() {
        return timetableRepository.findByTenantIdAndTeacherUserIdOrderByDayOfWeekAscStartTimeAsc(
                tid(), TenantContext.getUserId());
    }

    @Transactional(readOnly = true)
    public List<TimetableSlot> teacherToday(int isoDay) {
        return timetableRepository.findByTenantIdAndTeacherUserIdAndDayOfWeek(
                tid(), TenantContext.getUserId(), isoDay);
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
}
