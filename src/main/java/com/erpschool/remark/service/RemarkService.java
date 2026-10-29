package com.erpschool.remark.service;

import com.erpschool.academic.repository.TeacherAssignmentRepository;
import com.erpschool.common.dto.PageResponse;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.notification.service.NotificationService;
import com.erpschool.remark.entity.StudentRemark;
import com.erpschool.remark.repository.StudentRemarkRepository;
import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.student.service.StudentAccessService;
import com.erpschool.tenant.context.CampusScope;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RemarkService {

    private static final Set<String> CATEGORIES = Set.of(
            "APPRECIATION", "COMPLAINT", "ACADEMIC", "BEHAVIOUR",
            "PARTICIPATION", "HOMEWORK", "GENERAL");

    private final StudentRemarkRepository repository;
    private final StudentRepository studentRepository;
    private final StudentAccessService studentAccessService;
    private final TeacherAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public RemarkService(StudentRemarkRepository repository,
                         StudentRepository studentRepository,
                         StudentAccessService studentAccessService,
                         TeacherAssignmentRepository assignmentRepository,
                         UserRepository userRepository,
                         NotificationService notificationService) {
        this.repository = repository;
        this.studentRepository = studentRepository;
        this.studentAccessService = studentAccessService;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public List<Map<String, Object>> create(Map<String, Object> body) {
        assertStaffWrite();
        UUID tenantId = TenantGuard.requireTenantId(null);
        String category = requireCategory(str(body.get("category"), "GENERAL"));
        String text = str(body.get("body"), "");
        if (text.isBlank()) {
            throw new BusinessException("Remark text is required");
        }
        LocalDate date = parseDate(body.get("remarkDate"));
        List<UUID> studentIds = uuidList(body.get("studentIds"));
        UUID classId = uuid(body.get("classId"));
        UUID sectionId = uuid(body.get("sectionId"));
        if (studentIds.isEmpty()) {
            if (classId == null || sectionId == null) {
                throw new BusinessException("Select students, or a class and section");
            }
            assertTeacherAssigned(tenantId, classId, sectionId);
            studentIds = studentRepository
                    .findByTenantIdAndClassIdAndSectionIdAndStatus(tenantId, classId, sectionId, StudentStatus.ACTIVE)
                    .stream().map(Student::getId).toList();
        }
        if (studentIds.isEmpty()) {
            throw new BusinessException("No students matched this selection");
        }
        List<Student> students = studentRepository.findAllById(studentIds);
        Map<UUID, Student> byId = students.stream().collect(Collectors.toMap(Student::getId, s -> s, (a, b) -> a));
        Map<UUID, String> names = namesOf(students);
        List<Map<String, Object>> out = new ArrayList<>();
        for (UUID sid : studentIds) {
            Student student = byId.get(sid);
            if (student == null) {
                throw new ResourceNotFoundException("Student", sid);
            }
            TenantGuard.assertSameTenant(student.getTenantId());
            studentAccessService.assertCanView(student);
            assertTeacherAssigned(tenantId, student.getClassId(), student.getSectionId());
            StudentRemark row = new StudentRemark();
            row.setTenantId(tenantId);
            row.setCampusId(student.getCampusId());
            row.setStudentId(student.getId());
            row.setClassId(student.getClassId());
            row.setSectionId(student.getSectionId());
            row.setCategory(category);
            row.setBody(text);
            row.setRemarkDate(date);
            row.setCreatedBy(TenantContext.getUserId());
            StudentRemark saved = repository.save(row);
            String child = names.getOrDefault(student.getId(), "your child");
            notificationService.notifyUser(tenantId, student.getUserId(), "REMARK",
                    "New remark: " + categoryLabel(category), text, "Remark", saved.getId().toString());
            notificationService.notifyParents(tenantId, student.getId(), "REMARK",
                    "New remark — " + categoryLabel(category), text, "Remark", saved.getId().toString());
            out.add(toMap(saved, child));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> update(UUID id, Map<String, Object> body) {
        StudentRemark row = requireOwnManage(id);
        if (body.containsKey("category")) {
            row.setCategory(requireCategory(str(body.get("category"), row.getCategory())));
        }
        if (body.containsKey("body")) {
            String text = str(body.get("body"), "");
            if (text.isBlank()) {
                throw new BusinessException("Remark text is required");
            }
            row.setBody(text);
        }
        if (body.containsKey("remarkDate")) {
            row.setRemarkDate(parseDate(body.get("remarkDate")));
        }
        row.setUpdatedBy(TenantContext.getUserId());
        return toMap(repository.save(row), nameOf(row.getStudentId()));
    }

    @Transactional
    public void delete(UUID id) {
        StudentRemark row = requireOwnManage(id);
        repository.delete(row);
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(UUID studentId, UUID classId, UUID sectionId,
                                                  LocalDate from, LocalDate to, Pageable pageable) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(90);
        UserRole role = TenantContext.getRole();
        Page<StudentRemark> page;
        if (studentId != null) {
            Student student = studentAccessService.requireStudent(studentId);
            page = repository.findByTenantIdAndStudentIdOrderByRemarkDateDescCreatedAtDesc(
                    student.getTenantId(), student.getId(), pageable);
        } else if (role == UserRole.STUDENT) {
            Student me = studentRepository.findByTenantIdAndUserId(tenantId, TenantContext.getUserId())
                    .orElseThrow(() -> new BusinessException("No student profile is linked to this account"));
            page = repository.findByTenantIdAndStudentIdOrderByRemarkDateDescCreatedAtDesc(
                    tenantId, me.getId(), pageable);
        } else if (role == UserRole.PARENT) {
            throw new BusinessException("Select a child to view remarks");
        } else {
            assertStaffRead();
            if (classId != null && sectionId != null) {
                assertTeacherAssigned(tenantId, classId, sectionId);
                page = repository.findByTenantIdAndClassIdAndSectionIdAndRemarkDateBetweenOrderByRemarkDateDescCreatedAtDesc(
                        tenantId, classId, sectionId, start, end, pageable);
            } else if (CampusScope.restricts() && CampusScope.current() != null) {
                page = repository.findByTenantIdAndCampusIdAndRemarkDateBetweenOrderByRemarkDateDescCreatedAtDesc(
                        tenantId, CampusScope.current(), start, end, pageable);
            } else {
                page = repository.findByTenantIdAndRemarkDateBetweenOrderByRemarkDateDescCreatedAtDesc(
                        tenantId, start, end, pageable);
            }
        }
        Map<UUID, String> names = namesOf(studentRepository.findAllById(
                page.getContent().stream().map(StudentRemark::getStudentId).distinct().toList()));
        return PageResponse.from(page.map(r -> toMap(r, names.get(r.getStudentId()))));
    }

    private StudentRemark requireOwnManage(UUID id) {
        assertStaffWrite();
        StudentRemark row = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Remark", id));
        TenantGuard.assertSameTenant(row.getTenantId());
        UserRole role = TenantContext.getRole();
        if (role == UserRole.TEACHER && !TenantContext.getUserId().equals(row.getCreatedBy())) {
            throw new ForbiddenException("Teachers can only edit remarks they created");
        }
        assertTeacherAssigned(row.getTenantId(), row.getClassId(), row.getSectionId());
        return row;
    }

    private void assertStaffWrite() {
        UserRole role = TenantContext.getRole();
        if (role != UserRole.SCHOOL_ADMIN && role != UserRole.PRINCIPAL
                && role != UserRole.TEACHER && role != UserRole.ERP_OWNER) {
            throw new ForbiddenException("Not allowed to write remarks");
        }
    }

    private void assertStaffRead() {
        assertStaffWrite();
    }

    private void assertTeacherAssigned(UUID tenantId, UUID classId, UUID sectionId) {
        if (TenantContext.getRole() != UserRole.TEACHER) {
            return;
        }
        if (classId == null || sectionId == null) {
            throw new ForbiddenException("Teachers can only remark assigned classes");
        }
        boolean ok = assignmentRepository.findByTenantIdAndTeacherUserId(tenantId, TenantContext.getUserId())
                .stream()
                .anyMatch(a -> classId.equals(a.getClassId()) && sectionId.equals(a.getSectionId()));
        if (!ok) {
            throw new ForbiddenException("Teachers can only remark students in assigned classes");
        }
    }

    private Map<UUID, String> namesOf(List<Student> students) {
        List<UUID> userIds = students.stream().map(Student::getUserId).filter(id -> id != null).toList();
        Map<UUID, String> byUser = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User u : userRepository.findAllById(userIds)) {
                byUser.put(u.getId(), u.getFullName());
            }
        }
        Map<UUID, String> out = new HashMap<>();
        for (Student s : students) {
            out.put(s.getId(), byUser.getOrDefault(s.getUserId(), s.getAdmissionNumber()));
        }
        return out;
    }

    private String nameOf(UUID studentId) {
        return studentRepository.findById(studentId)
                .map(s -> namesOf(List.of(s)).get(s.getId()))
                .orElse(null);
    }

    private Map<String, Object> toMap(StudentRemark r, String studentName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("studentId", r.getStudentId());
        m.put("studentName", studentName);
        m.put("classId", r.getClassId());
        m.put("sectionId", r.getSectionId());
        m.put("campusId", r.getCampusId());
        m.put("category", r.getCategory());
        m.put("body", r.getBody());
        m.put("remarkDate", r.getRemarkDate());
        m.put("createdAt", r.getCreatedAt());
        m.put("createdBy", r.getCreatedBy());
        m.put("mine", TenantContext.getUserId() != null && TenantContext.getUserId().equals(r.getCreatedBy()));
        return m;
    }

    private static String requireCategory(String raw) {
        String c = raw == null ? "" : raw.trim().toUpperCase();
        if (!CATEGORIES.contains(c)) {
            throw new BusinessException("Unknown remark category");
        }
        return c;
    }

    private static String categoryLabel(String c) {
        if (c == null) return "Remark";
        return c.charAt(0) + c.substring(1).toLowerCase().replace('_', ' ');
    }

    private static LocalDate parseDate(Object v) {
        if (v == null || v.toString().isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(v.toString().trim().substring(0, 10));
        } catch (RuntimeException e) {
            throw new BusinessException("Remark date is invalid");
        }
    }

    private static String str(Object v, String d) {
        if (v == null) return d;
        String s = v.toString().trim();
        return s.isEmpty() ? d : s;
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
}
