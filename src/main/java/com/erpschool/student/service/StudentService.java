package com.erpschool.student.service;

import com.erpschool.academic.entity.SchoolClass;
import com.erpschool.academic.entity.Section;
import com.erpschool.academic.service.AcademicService;
import com.erpschool.audit.service.AuditService;
import com.erpschool.common.dto.PageResponse;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.DuplicateResourceException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.service.DocumentSequenceService;
import com.erpschool.common.util.Cnic;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.tenant.context.CampusScope;
import com.erpschool.student.dto.StudentDtos;
import com.erpschool.student.entity.ParentStudent;
import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import com.erpschool.student.entity.StudentStatusEvent;
import com.erpschool.student.repository.ParentStudentRepository;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.student.repository.StudentStatusEventRepository;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.dto.CreateUserRequest;
import com.erpschool.user.dto.CreateUserResponse;
import com.erpschool.user.dto.UserResponse;
import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.entity.UserStatus;
import com.erpschool.user.repository.UserRepository;
import com.erpschool.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final StudentAccessService studentAccessService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final AcademicService academicService;
    private final DocumentSequenceService documentSequenceService;
    private final AuditService auditService;
    private final StudentStatusEventRepository statusEventRepository;

    private static final UUID NIL = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public StudentService(StudentRepository studentRepository,
                          ParentStudentRepository parentStudentRepository,
                          StudentAccessService studentAccessService,
                          UserService userService,
                          UserRepository userRepository,
                          TenantRepository tenantRepository,
                          AcademicService academicService,
                          DocumentSequenceService documentSequenceService,
                          AuditService auditService,
                          StudentStatusEventRepository statusEventRepository) {
        this.studentRepository = studentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.studentAccessService = studentAccessService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.academicService = academicService;
        this.documentSequenceService = documentSequenceService;
        this.auditService = auditService;
        this.statusEventRepository = statusEventRepository;
    }

    @Transactional
    public StudentDtos.EnrollResponse enroll(StudentDtos.EnrollRequest request) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("School not found"));
        academicService.requireClass(request.getClassId());
        academicService.requireSection(request.getSectionId());

        String cnic = Cnic.normalize(request.getCnic());
        if (studentRepository.findByTenantIdAndCnic(tenantId, cnic).isPresent()) {
            throw new DuplicateResourceException("A student with this CNIC / B-Form already exists");
        }

        CreateUserRequest studentUserReq = new CreateUserRequest();
        studentUserReq.setTenantId(tenantId);
        studentUserReq.setFirstName(request.getFirstName());
        studentUserReq.setLastName(request.getLastName());
        studentUserReq.setEmail(request.getEmail());
        studentUserReq.setPhone(request.getPhone());
        studentUserReq.setRole(UserRole.STUDENT);
        CreateUserResponse studentAccount = userService.create(studentUserReq);

        String admission = documentSequenceService.nextFormatted(
                tenantId, "ADM", tenant.getCode().toUpperCase() + "-ADM-");

        Student student = new Student();
        student.setTenantId(tenantId);
        student.setUserId(studentAccount.getUser().getId());
        student.setCampusId(request.getCampusId());
        student.setClassId(request.getClassId());
        student.setSectionId(request.getSectionId());
        student.setAdmissionNumber(admission);
        student.setRegistrationNumber(admission);
        student.setRollNumber(request.getRollNumber());
        student.setCnic(cnic);
        student.setPhotoUrl(request.getPhotoUrl());
        student.setGender(request.getGender());
        student.setDateOfBirth(request.getDateOfBirth());
        student.setAdmissionDate(request.getAdmissionDate() == null ? LocalDate.now() : request.getAdmissionDate());
        student.setStatus(StudentStatus.ACTIVE);
        student.setAddress(request.getAddress());
        student.setGuardianName(request.getGuardianName());
        student.setGuardianPhone(request.getGuardianPhone());
        student.setCreatedBy(TenantContext.getUserId());
        student = studentRepository.save(student);

        UUID studentUserId = studentAccount.getUser().getId();
        userRepository.findById(studentUserId).ifPresent(account -> {
            userRepository.findByUsernameIgnoreCase(cnic)
                    .filter(existing -> !existing.getId().equals(studentUserId))
                    .ifPresent(existing -> {
                        throw new DuplicateResourceException("Username (CNIC) is already in use");
                    });
            account.setUsername(cnic);
            if (request.getCampusId() != null) {
                account.setCampusId(request.getCampusId());
            }
            userRepository.save(account);
        });

        CreateUserResponse parentAccount = null;
        UUID parentUserId = request.getParentUserId();
        if (parentUserId == null && request.getParentFirstName() != null && !request.getParentFirstName().isBlank()) {
            CreateUserRequest parentReq = new CreateUserRequest();
            parentReq.setTenantId(tenantId);
            parentReq.setFirstName(request.getParentFirstName());
            parentReq.setLastName(request.getParentLastName() == null ? "Parent" : request.getParentLastName());
            parentReq.setEmail(request.getParentEmail());
            parentReq.setPhone(request.getParentPhone());
            parentReq.setRole(UserRole.PARENT);
            parentAccount = userService.create(parentReq);
            parentUserId = parentAccount.getUser().getId();
        }
        if (parentUserId != null) {
            linkParent(student.getId(), parentUserId, request.getRelationship() == null ? "GUARDIAN" : request.getRelationship());
        }

        auditService.record("STUDENT_ENROLLED", "Student", student.getId().toString(),
                Map.of("admissionNumber", admission));
        return StudentDtos.EnrollResponse.builder()
                .student(StudentDtos.from(student, studentAccount.getUser()))
                .studentAccount(studentAccount)
                .parentAccount(parentAccount)
                .message("Student enrolled. Share generated usernames and temporary passwords securely.")
                .build();
    }

    @Transactional
    public ParentStudent linkParent(UUID studentId, UUID parentUserId, String relationship) {
        Student student = studentAccessService.requireStudent(studentId);
        User parent = userRepository.findById(parentUserId)
                .orElseThrow(() -> new BusinessException("Parent user not found"));
        TenantGuard.assertSameTenant(parent.getTenantId());
        if (parent.getRole() != UserRole.PARENT) {
            throw new BusinessException("User is not a parent");
        }
        if (parentStudentRepository.existsByTenantIdAndParentUserIdAndStudentId(
                student.getTenantId(), parentUserId, studentId)) {
            throw new DuplicateResourceException("Parent is already linked to this student");
        }
        ParentStudent link = new ParentStudent();
        link.setTenantId(student.getTenantId());
        link.setParentUserId(parentUserId);
        link.setStudentId(studentId);
        link.setRelationship(relationship);
        link.setPrimaryLink(true);
        link.setCreatedBy(TenantContext.getUserId());
        return parentStudentRepository.save(link);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentDtos.ListRow> list(UUID classId, UUID sectionId, String academicSession, String q,
                                                  StudentStatus status, Pageable pageable) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        UUID campusId = CampusScope.restricts() ? CampusScope.current() : null;
        java.util.Set<UUID> sessionClassIds = null;
        if (academicSession != null && !academicSession.isBlank()) {
            sessionClassIds = academicService.classes().stream()
                    .filter(c -> academicSession.equalsIgnoreCase(c.getAcademicSession()))
                    .map(SchoolClass::getId)
                    .collect(Collectors.toSet());
            if (sessionClassIds.isEmpty() || (classId != null && !sessionClassIds.contains(classId))) {
                return emptyListRows(Page.empty(pageable));
            }
        }
        boolean filterClasses = classId == null && sessionClassIds != null;
        Collection<UUID> classIds = filterClasses ? sessionClassIds : List.of(NIL);
        String like = normalizeSearch(q);
        Page<Student> page = like == null
                ? studentRepository.searchFiltered(
                        tenantId, campusId, classId, sectionId, status, filterClasses, classIds, pageable)
                : studentRepository.search(
                        tenantId, campusId, classId, sectionId, status, filterClasses, classIds, like, pageable);
        Map<UUID, UserResponse> users = usersById(page.getContent());
        List<StudentDtos.ListRow> content = page.getContent().stream()
                .map(s -> StudentDtos.listRow(s, users.get(s.getUserId())))
                .toList();
        return PageResponse.<StudentDtos.ListRow>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> roster(UUID classId, UUID sectionId, StudentStatus status) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        SchoolClass clazz = academicService.requireClass(classId);
        assertClassOnCampus(clazz);
        if (sectionId != null) {
            Section section = academicService.requireSection(sectionId);
            if (!classId.equals(section.getClassId())) {
                throw new BusinessException("INVALID_SECTION", "Section does not belong to the selected class");
            }
        }
        UUID campusId = CampusScope.restricts() ? CampusScope.current() : null;
        StudentStatus st = status == null ? StudentStatus.ACTIVE : status;
        List<Student> rows = studentRepository.findRoster(tenantId, campusId, classId, sectionId, st);
        if (rows.size() > 2000) {
            rows = rows.subList(0, 2000);
        }
        Map<UUID, UserResponse> users = usersById(rows);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Student s : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("userId", s.getUserId());
            m.put("campusId", s.getCampusId());
            m.put("classId", s.getClassId());
            m.put("sectionId", s.getSectionId());
            m.put("admissionNumber", s.getAdmissionNumber());
            m.put("rollNumber", s.getRollNumber());
            m.put("status", s.getStatus());
            UserResponse u = s.getUserId() == null ? null : users.get(s.getUserId());
            m.put("fullName", u != null ? u.getFullName() : null);
            out.add(m);
        }
        return out;
    }

    private static String normalizeSearch(String q) {
        if (q == null) {
            return null;
        }
        String trimmed = q.trim().toLowerCase().replace("%", "").replace("_", "");
        if (trimmed.isEmpty()) {
            return null;
        }
        return "%" + trimmed + "%";
    }

    private static PageResponse<StudentDtos.ListRow> emptyListRows(Page<Student> page) {
        return PageResponse.<StudentDtos.ListRow>builder()
                .content(List.of())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .build();
    }

    @Transactional(readOnly = true)
    public StudentDtos.Response get(UUID id) {
        Student s = studentAccessService.requireStudent(id);
        return StudentDtos.from(s, loadUser(s.getUserId()));
    }

    @Transactional(readOnly = true)
    public StudentDtos.Response meAsStudent() {
        Student s = requireLinkedStudent();
        return StudentDtos.from(s, loadUser(s.getUserId()));
    }

    @Transactional(readOnly = true)
    public StudentDtos.ListRow meAsStudentRow() {
        Student s = requireLinkedStudent();
        return StudentDtos.listRow(s, loadUser(s.getUserId()));
    }

    private Student requireLinkedStudent() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        return studentRepository.findByTenantIdAndUserId(tenantId, TenantContext.getUserId())
                .orElseThrow(() -> new BusinessException("No student profile is linked to this account"));
    }

    @Transactional(readOnly = true)
    public List<StudentDtos.Response> myChildren() {
        List<Student> students = myChildrenStudents();
        Map<UUID, UserResponse> users = usersById(students);
        return students.stream()
                .map(s -> StudentDtos.from(s, users.get(s.getUserId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StudentDtos.ListRow> myChildrenRows() {
        List<Student> students = myChildrenStudents();
        Map<UUID, UserResponse> users = usersById(students);
        return students.stream()
                .map(s -> StudentDtos.listRow(s, users.get(s.getUserId())))
                .toList();
    }

    private List<Student> myChildrenStudents() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        List<UUID> studentIds = parentStudentRepository.findByTenantIdAndParentUserId(tenantId, TenantContext.getUserId())
                .stream()
                .map(ParentStudent::getStudentId)
                .toList();
        if (studentIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Student> students = studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        return studentIds.stream()
                .map(students::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public StudentDtos.Response update(UUID id, StudentDtos.UpdateRequest request) {
        Student s = studentAccessService.requireStudent(id);
        if (request.getCampusId() != null) s.setCampusId(request.getCampusId());
        if (request.getClassId() != null) {
            academicService.requireClass(request.getClassId());
            s.setClassId(request.getClassId());
        }
        if (request.getSectionId() != null) {
            academicService.requireSection(request.getSectionId());
            s.setSectionId(request.getSectionId());
        }
        if (request.getRollNumber() != null) s.setRollNumber(request.getRollNumber());
        if (request.getCnic() != null) {
            String cnic = Cnic.normalize(request.getCnic());
            UUID studentId = s.getId();
            studentRepository.findByTenantIdAndCnic(s.getTenantId(), cnic)
                    .filter(existing -> !existing.getId().equals(studentId))
                    .ifPresent(existing -> {
                        throw new DuplicateResourceException("A student with this CNIC / B-Form already exists");
                    });
            s.setCnic(cnic);
            if (s.getUserId() != null) {
                UUID studentUserId = s.getUserId();
                userRepository.findById(studentUserId).ifPresent(account -> {
                    userRepository.findByUsernameIgnoreCase(cnic)
                            .filter(existing -> !existing.getId().equals(studentUserId))
                            .ifPresent(existing -> {
                                throw new DuplicateResourceException("Username (CNIC) is already in use");
                            });
                    account.setUsername(cnic);
                    userRepository.save(account);
                });
            }
        }
        if (request.getPhotoUrl() != null) s.setPhotoUrl(request.getPhotoUrl());
        if (request.getGender() != null) s.setGender(request.getGender());
        if (request.getDateOfBirth() != null) s.setDateOfBirth(request.getDateOfBirth());
        if (request.getAddress() != null) s.setAddress(request.getAddress());
        if (request.getGuardianName() != null) s.setGuardianName(request.getGuardianName());
        if (request.getGuardianPhone() != null) s.setGuardianPhone(request.getGuardianPhone());
        if (request.getStatus() != null) s.setStatus(request.getStatus());
        s.setUpdatedBy(TenantContext.getUserId());
        s = studentRepository.save(s);
        return StudentDtos.from(s, loadUser(s.getUserId()));
    }

    @Transactional
    public List<StudentDtos.Response> promote(UUID fromClassId, UUID fromSectionId, UUID toClassId, UUID toSectionId,
                                              List<UUID> studentIds) {
        academicService.requireClass(toClassId);
        academicService.requireSection(toSectionId);
        UUID tenantId = TenantGuard.requireTenantId(null);
        UUID campusId = CampusScope.restricts() ? CampusScope.current() : null;
        List<Student> targets;
        if (studentIds != null && !studentIds.isEmpty()) {
            targets = studentRepository.findAllById(studentIds);
        } else if (fromSectionId != null) {
            targets = studentRepository.findByTenantIdAndClassIdAndSectionIdAndStatus(
                    tenantId, fromClassId, fromSectionId, StudentStatus.ACTIVE);
        } else {
            targets = studentRepository.findByTenantIdAndClassIdAndStatus(
                    tenantId, fromClassId, StudentStatus.ACTIVE);
        }
        List<Student> saved = new ArrayList<>();
        for (Student s : targets) {
            TenantGuard.assertSameTenant(s.getTenantId());
            if (campusId != null && s.getCampusId() != null && !campusId.equals(s.getCampusId())) {
                throw new ForbiddenException("Student is not in your campus");
            }
            s.setClassId(toClassId);
            s.setSectionId(toSectionId);
            s.setUpdatedBy(TenantContext.getUserId());
            saved.add(studentRepository.save(s));
        }
        Map<UUID, UserResponse> users = usersById(saved);
        List<StudentDtos.Response> out = saved.stream()
                .map(s -> StudentDtos.from(s, users.get(s.getUserId())))
                .toList();
        auditService.record("STUDENTS_PROMOTED", "Student", null, Map.of("count", out.size()));
        return out;
    }

    @Transactional
    public StudentDtos.Response inactivate(UUID id, String reason, String note) {
        Student s = studentAccessService.requireStudent(id);
        if (s.getStatus() != StudentStatus.ACTIVE) {
            throw new BusinessException("ALREADY_INACTIVE", "This student is not active");
        }
        StatusReason parsed = parseReason(reason, note);
        StudentStatus previous = s.getStatus();
        s.setStatus(parsed.status());
        s.setInactiveReason(parsed.code());
        s.setInactiveNote(parsed.note());
        s.setUpdatedBy(TenantContext.getUserId());
        s = studentRepository.save(s);
        if (s.getUserId() != null) {
            Instant now = Instant.now();
            inactivateUserAccounts(s.getTenantId(), List.of(s.getUserId()), TenantContext.getUserId(), now);
        }
        recordEvent(s, previous, parsed.status(), parsed.code(), parsed.note(), false);
        auditService.record("STUDENT_INACTIVATED", "Student", s.getId().toString(), eventAudit(s, previous, parsed));
        return StudentDtos.from(s, loadUser(s.getUserId()));
    }

    @Transactional
    public StudentDtos.Response reactivate(UUID id, String note) {
        Student s = studentAccessService.requireStudent(id);
        if (s.getStatus() == StudentStatus.ACTIVE) {
            throw new BusinessException("ALREADY_ACTIVE", "This student is already active");
        }
        StudentStatus previous = s.getStatus();
        String noteText = trimNote(note);
        s.setStatus(StudentStatus.ACTIVE);
        s.setUpdatedBy(TenantContext.getUserId());
        s = studentRepository.save(s);
        if (s.getUserId() != null) {
            Instant now = Instant.now();
            userRepository.updateStatusForIds(
                    s.getTenantId(), List.of(s.getUserId()), UserStatus.ACTIVE, now, TenantContext.getUserId());
        }
        recordEvent(s, previous, StudentStatus.ACTIVE, "REACTIVATED", noteText, false);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fromStatus", previous.name());
        details.put("toStatus", StudentStatus.ACTIVE.name());
        details.put("reason", "REACTIVATED");
        if (noteText != null) {
            details.put("note", noteText);
        }
        details.put("campusId", s.getCampusId());
        details.put("keptReason", s.getInactiveReason());
        auditService.record("STUDENT_REACTIVATED", "Student", s.getId().toString(), details);
        return StudentDtos.from(s, loadUser(s.getUserId()));
    }

    @Transactional(readOnly = true)
    public List<StudentDtos.StatusEventRow> statusHistory(UUID id) {
        Student s = studentAccessService.requireStudent(id);
        return statusEventRepository.findByTenantIdAndStudentIdOrderByCreatedAtDesc(s.getTenantId(), s.getId())
                .stream()
                .limit(50)
                .map(StudentDtos::eventRow)
                .toList();
    }

    @Transactional
    public Map<String, Object> inactivateBulk(UUID classId, UUID sectionId, String reason, String note) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        UUID actor = TenantContext.getUserId();
        SchoolClass clazz = academicService.requireClass(classId);
        assertClassOnCampus(clazz);
        String sectionName = null;
        if (sectionId != null) {
            Section section = academicService.requireSection(sectionId);
            if (!classId.equals(section.getClassId())) {
                throw new BusinessException("INVALID_SECTION", "Section does not belong to the selected class");
            }
            sectionName = section.getName();
        }
        UUID campusId = CampusScope.restricts() ? CampusScope.current() : null;
        StatusReason parsed = parseReason(reason, note);
        long expected = studentRepository.countInScope(tenantId, classId, sectionId, campusId, StudentStatus.ACTIVE);
        if (expected <= 0) {
            throw new BusinessException("NO_STUDENTS", "No active students in this class/section");
        }
        List<UUID> userIds = studentRepository.findUserIdsInScope(
                tenantId, classId, sectionId, campusId, StudentStatus.ACTIVE);
        Instant now = Instant.now();
        statusEventRepository.insertBulkEvents(
                tenantId,
                classId,
                sectionId != null,
                sectionId == null ? NIL : sectionId,
                campusId != null,
                campusId == null ? NIL : campusId,
                StudentStatus.ACTIVE.name(),
                parsed.status().name(),
                parsed.code(),
                parsed.note(),
                actor,
                TenantContext.getUsername(),
                now);
        int updated = studentRepository.bulkUpdateStatusInScope(
                tenantId, classId, sectionId, campusId, StudentStatus.ACTIVE, parsed.status(),
                parsed.code(), parsed.note(), actor, now);
        inactivateUserAccounts(tenantId, userIds, actor, now);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", parsed.code());
        if (parsed.note() != null) {
            details.put("note", parsed.note());
        }
        details.put("fromStatus", StudentStatus.ACTIVE.name());
        details.put("toStatus", parsed.status().name());
        details.put("count", updated);
        details.put("classId", classId.toString());
        if (sectionId != null) {
            details.put("sectionId", sectionId.toString());
        }
        if (campusId != null) {
            details.put("campusId", campusId.toString());
        }
        auditService.record("STUDENT_BULK_INACTIVATED", "Student", classId.toString(), details);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inactivated", updated);
        out.put("classId", classId);
        out.put("className", clazz.getName());
        out.put("sectionId", sectionId);
        out.put("sectionName", sectionName);
        out.put("reason", parsed.code());
        out.put("note", parsed.note());
        out.put("status", parsed.status());
        return out;
    }

    /**
     * Canonical student statuses are ACTIVE, INACTIVE, GRADUATED, LEFT_SCHOOL
     * (see chk_student_status). TRANSFERRED is accepted only as a reason alias
     * for leaving the school — it is not an enum value and is not stored as status.
     */
    public static StudentStatus statusForReason(String code) {
        if (code == null || code.isBlank()) {
            return StudentStatus.INACTIVE;
        }
        if ("GRADUATED".equals(code) || "GRADE_10_COMPLETE".equals(code)) {
            return StudentStatus.GRADUATED;
        }
        if ("LEFT_SCHOOL".equals(code) || "TRANSFERRED".equals(code)) {
            return StudentStatus.LEFT_SCHOOL;
        }
        return StudentStatus.INACTIVE;
    }

    public record StatusReason(String code, String note, StudentStatus status) {
    }

    public static StatusReason parseReason(String reason, String note) {
        String code = reason == null ? "" : reason.trim().toUpperCase().replace(' ', '_');
        if ("FEE_NOT_PAID".equals(code) || "FEE_UNPAID".equals(code)) {
            code = "UNPAID_FEES";
        }
        if (code.isBlank()) {
            throw new BusinessException("REASON_REQUIRED", "A reason is required");
        }
        if (!java.util.Set.of("UNPAID_FEES", "LEFT_SCHOOL", "TRANSFERRED", "PARENT_REQUEST", "GRADUATED", "OTHER")
                .contains(code)) {
            throw new BusinessException("REASON_INVALID", "Choose a valid reason");
        }
        String trimmed = trimNote(note);
        if ("OTHER".equals(code) && trimmed == null) {
            throw new BusinessException("REASON_REQUIRED", "Describe the reason when Other is selected");
        }
        return new StatusReason(code, trimmed, statusForReason(code));
    }

    private static String trimNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }

    private void recordEvent(Student student, StudentStatus from, StudentStatus to,
                             String reasonCode, String reasonNote, boolean bulk) {
        StudentStatusEvent event = new StudentStatusEvent();
        event.setTenantId(student.getTenantId());
        event.setCampusId(student.getCampusId());
        event.setStudentId(student.getId());
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setReasonCode(reasonCode);
        event.setReasonNote(reasonNote);
        event.setActorUserId(TenantContext.getUserId());
        event.setActorUsername(TenantContext.getUsername());
        event.setBulk(bulk);
        event.setCreatedAt(Instant.now());
        statusEventRepository.save(event);
    }

    private static Map<String, Object> eventAudit(Student student, StudentStatus previous, StatusReason parsed) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fromStatus", previous.name());
        details.put("toStatus", parsed.status().name());
        details.put("reason", parsed.code());
        if (parsed.note() != null) {
            details.put("note", parsed.note());
        }
        details.put("campusId", student.getCampusId());
        details.put("tenantId", student.getTenantId());
        return details;
    }

    private void assertClassOnCampus(SchoolClass clazz) {
        UUID campus = CampusScope.restricts() ? CampusScope.current() : null;
        if (campus != null && clazz.getCampusId() != null && !campus.equals(clazz.getCampusId())) {
            throw new ForbiddenException("Class is not in your campus");
        }
    }

    private void inactivateUserAccounts(UUID tenantId, List<UUID> userIds, UUID actor, Instant now) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<UUID> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        for (int i = 0; i < ids.size(); i += 500) {
            List<UUID> chunk = ids.subList(i, Math.min(i + 500, ids.size()));
            userRepository.updateStatusForIds(tenantId, chunk, UserStatus.INACTIVE, now, actor);
        }
    }

    private Map<UUID, UserResponse> usersById(List<Student> students) {
        List<UUID> userIds = students.stream().map(Student::getUserId).filter(Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, UserResponse::from));
    }

    private UserResponse loadUser(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(UserResponse::from).orElse(null);
    }
}
