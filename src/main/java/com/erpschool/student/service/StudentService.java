package com.erpschool.student.service;

import com.erpschool.academic.service.AcademicService;
import com.erpschool.audit.service.AuditService;
import com.erpschool.common.dto.PageResponse;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.DuplicateResourceException;
import com.erpschool.common.service.DocumentSequenceService;
import com.erpschool.common.util.Cnic;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.tenant.context.CampusScope;
import com.erpschool.student.dto.StudentDtos;
import com.erpschool.student.entity.ParentStudent;
import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import com.erpschool.student.repository.ParentStudentRepository;
import com.erpschool.student.repository.StudentRepository;
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

import java.time.LocalDate;
import java.util.ArrayList;
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

    public StudentService(StudentRepository studentRepository,
                          ParentStudentRepository parentStudentRepository,
                          StudentAccessService studentAccessService,
                          UserService userService,
                          UserRepository userRepository,
                          TenantRepository tenantRepository,
                          AcademicService academicService,
                          DocumentSequenceService documentSequenceService,
                          AuditService auditService) {
        this.studentRepository = studentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.studentAccessService = studentAccessService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.academicService = academicService;
        this.documentSequenceService = documentSequenceService;
        this.auditService = auditService;
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
    public PageResponse<StudentDtos.Response> list(UUID classId, UUID sectionId, String academicSession, Pageable pageable) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        Page<Student> page;
        UUID campusId = CampusScope.restricts() ? CampusScope.current() : null;
        java.util.Set<UUID> sessionClassIds = null;
        if (academicSession != null && !academicSession.isBlank()) {
            sessionClassIds = academicService.classes().stream()
                    .filter(c -> academicSession.equalsIgnoreCase(c.getAcademicSession()))
                    .map(com.erpschool.academic.entity.SchoolClass::getId)
                    .collect(Collectors.toSet());
            if (sessionClassIds.isEmpty() || (classId != null && !sessionClassIds.contains(classId))) {
                page = Page.empty(pageable);
                return emptyStudents(page);
            }
        }
        if (classId == null && sessionClassIds != null) {
            if (campusId != null) {
                page = studentRepository.findByTenantIdAndCampusIdAndClassIdIn(tenantId, campusId, sessionClassIds, pageable);
            } else {
                page = studentRepository.findByTenantIdAndClassIdIn(tenantId, sessionClassIds, pageable);
            }
        } else if (campusId != null && classId != null) {
            page = studentRepository.findByTenantIdAndCampusIdAndClassId(tenantId, campusId, classId, pageable);
        } else if (campusId != null) {
            page = studentRepository.findByTenantIdAndCampusId(tenantId, campusId, pageable);
        } else if (classId != null && sectionId != null) {
            page = studentRepository.findByTenantIdAndClassIdAndSectionId(tenantId, classId, sectionId, pageable);
        } else if (classId != null) {
            page = studentRepository.findByTenantIdAndClassId(tenantId, classId, pageable);
        } else if (sectionId != null) {
            page = studentRepository.findByTenantIdAndSectionId(tenantId, sectionId, pageable);
        } else {
            page = studentRepository.findByTenantId(tenantId, pageable);
        }
        Map<UUID, UserResponse> users = usersById(page.getContent());
        List<StudentDtos.Response> content = page.getContent().stream()
                .map(s -> StudentDtos.from(s, users.get(s.getUserId())))
                .toList();
        return PageResponse.<StudentDtos.Response>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    private static PageResponse<StudentDtos.Response> emptyStudents(Page<Student> page) {
        return PageResponse.<StudentDtos.Response>builder()
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
        UUID tenantId = TenantGuard.requireTenantId(null);
        Student s = studentRepository.findByTenantIdAndUserId(tenantId, TenantContext.getUserId())
                .orElseThrow(() -> new BusinessException("No student profile is linked to this account"));
        return StudentDtos.from(s, loadUser(s.getUserId()));
    }

    @Transactional(readOnly = true)
    public List<StudentDtos.Response> myChildren() {
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
        Map<UUID, UserResponse> users = usersById(students.values().stream().toList());
        return studentIds.stream()
                .map(students::get)
                .filter(Objects::nonNull)
                .map(s -> StudentDtos.from(s, users.get(s.getUserId())))
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
        List<Student> targets;
        if (studentIds != null && !studentIds.isEmpty()) {
            targets = studentRepository.findAllById(studentIds);
        } else if (fromSectionId != null) {
            targets = studentRepository.findByTenantIdAndClassIdAndSectionIdAndStatus(
                    TenantGuard.requireTenantId(null), fromClassId, fromSectionId, StudentStatus.ACTIVE);
        } else {
            targets = studentRepository.findByTenantIdAndClassIdAndStatus(
                    TenantGuard.requireTenantId(null), fromClassId, StudentStatus.ACTIVE);
        }
        List<StudentDtos.Response> out = new ArrayList<>();
        for (Student s : targets) {
            studentAccessService.requireStudent(s.getId());
            s.setClassId(toClassId);
            s.setSectionId(toSectionId);
            s.setUpdatedBy(TenantContext.getUserId());
            s = studentRepository.save(s);
            out.add(StudentDtos.from(s, loadUser(s.getUserId())));
        }
        auditService.record("STUDENTS_PROMOTED", "Student", null, Map.of("count", out.size()));
        return out;
    }

    @Transactional
    public StudentDtos.Response inactivate(UUID id, String reason) {
        Student s = studentAccessService.requireStudent(id);
        String code = reason == null ? "OTHER" : reason.trim().toUpperCase();
        if ("GRADUATED".equals(code) || "GRADE_10_COMPLETE".equals(code)) {
            s.setStatus(StudentStatus.GRADUATED);
        } else {
            s.setStatus(StudentStatus.INACTIVE);
        }
        s.setInactiveReason(code);
        s.setUpdatedBy(TenantContext.getUserId());
        s = studentRepository.save(s);
        if (s.getUserId() != null) {
            userRepository.findById(s.getUserId()).ifPresent(account -> {
                account.setStatus(UserStatus.INACTIVE);
                userRepository.save(account);
            });
        }
        auditService.record("STUDENT_INACTIVATED", "Student", s.getId().toString(), Map.of("reason", code));
        return StudentDtos.from(s, loadUser(s.getUserId()));
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
