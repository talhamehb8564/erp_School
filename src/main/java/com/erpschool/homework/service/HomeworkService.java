package com.erpschool.homework.service;

import com.erpschool.academic.service.AcademicService;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.DuplicateResourceException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.homework.entity.Homework;
import com.erpschool.homework.entity.HomeworkAttachment;
import com.erpschool.homework.entity.HomeworkSubmission;
import com.erpschool.homework.entity.HomeworkSubmissionStatus;
import com.erpschool.homework.repository.HomeworkAttachmentRepository;
import com.erpschool.homework.repository.HomeworkRepository;
import com.erpschool.homework.repository.HomeworkSubmissionRepository;
import com.erpschool.notification.service.NotificationService;
import com.erpschool.student.entity.ParentStudent;
import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import com.erpschool.student.repository.ParentStudentRepository;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.student.service.StudentAccessService;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.user.entity.UserRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HomeworkService {

    private final HomeworkRepository homeworkRepository;
    private final HomeworkAttachmentRepository attachmentRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final AcademicService academicService;
    private final StudentRepository studentRepository;
    private final StudentAccessService studentAccessService;
    private final ParentStudentRepository parentStudentRepository;
    private final NotificationService notificationService;

    public HomeworkService(HomeworkRepository homeworkRepository,
                           HomeworkAttachmentRepository attachmentRepository,
                           HomeworkSubmissionRepository submissionRepository,
                           AcademicService academicService,
                           StudentRepository studentRepository,
                           StudentAccessService studentAccessService,
                           ParentStudentRepository parentStudentRepository,
                           NotificationService notificationService) {
        this.homeworkRepository = homeworkRepository;
        this.attachmentRepository = attachmentRepository;
        this.submissionRepository = submissionRepository;
        this.academicService = academicService;
        this.studentRepository = studentRepository;
        this.studentAccessService = studentAccessService;
        this.parentStudentRepository = parentStudentRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Map<String, Object> create(Homework body, List<AttachmentIn> attachments) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        academicService.requireClass(body.getClassId());
        academicService.requireSection(body.getSectionId());
        academicService.requireSubject(body.getSubjectId());
        UUID teacherId = TenantContext.getRole() == UserRole.TEACHER
                ? TenantContext.getUserId() : body.getTeacherUserId();
        if (teacherId == null) {
            teacherId = TenantContext.getUserId();
        }
        if (TenantContext.getRole() == UserRole.TEACHER) {
            academicService.assertTeacherAssigned(teacherId, body.getClassId(), body.getSectionId(), body.getSubjectId());
        }
        body.setTenantId(tenantId);
        body.setTeacherUserId(teacherId);
        body.setCreatedBy(TenantContext.getUserId());
        Homework saved = homeworkRepository.save(body);
        final UUID homeworkId = saved.getId();
        final String homeworkTitle = saved.getTitle();
        final String homeworkBody = saved.getDescription();
        final UUID classId = saved.getClassId();
        final UUID sectionId = saved.getSectionId();
        if (attachments != null) {
            for (AttachmentIn a : attachments) {
                HomeworkAttachment ha = new HomeworkAttachment();
                ha.setTenantId(tenantId);
                ha.setHomeworkId(homeworkId);
                ha.setFileName(a.fileName());
                ha.setFileUrl(a.fileUrl());
                ha.setContentType(a.contentType());
                ha.setCreatedBy(TenantContext.getUserId());
                attachmentRepository.save(ha);
            }
        }
        studentRepository.findByTenantIdAndClassIdAndSectionIdAndStatus(
                        tenantId, classId, sectionId, StudentStatus.ACTIVE)
                .forEach(st -> {
                    notificationService.notifyUser(tenantId, st.getUserId(), "HOMEWORK",
                            "New homework: " + homeworkTitle, homeworkBody,
                            "Homework", homeworkId.toString());
                    parentStudentRepository.findByTenantIdAndStudentId(tenantId, st.getId())
                            .forEach(link -> notificationService.notifyUser(tenantId, link.getParentUserId(),
                                    "HOMEWORK", "Homework for your child: " + homeworkTitle,
                                    homeworkBody, "Homework", homeworkId.toString()));
                });
        return toMap(saved);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> forClass(UUID classId, UUID sectionId) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        if (classId == null || sectionId == null) {
            throw new BusinessException("CLASS_REQUIRED", "Class and section are required");
        }
        UserRole role = TenantContext.getRole();
        if (role == UserRole.STUDENT) {
            Student me = studentRepository.findByTenantIdAndUserId(tenantId, TenantContext.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
            if (me.getClassId() == null || me.getSectionId() == null
                    || !classId.equals(me.getClassId()) || !sectionId.equals(me.getSectionId())) {
                throw new ForbiddenException("Cannot view another class homework");
            }
        }
        if (role == UserRole.PARENT) {
            List<UUID> linkedIds = parentStudentRepository.findByTenantIdAndParentUserId(tenantId, TenantContext.getUserId())
                    .stream()
                    .map(ParentStudent::getStudentId)
                    .toList();
            boolean linked = !linkedIds.isEmpty() && studentRepository.findAllById(linkedIds).stream()
                    .anyMatch(s -> classId.equals(s.getClassId()) && sectionId.equals(s.getSectionId()));
            if (!linked) {
                throw new com.erpschool.common.exception.ForbiddenException("Cannot view homework for unrelated classes");
            }
        }
        return toMaps(homeworkRepository.findByTenantIdAndClassIdAndSectionIdOrderByDueDateDesc(
                tenantId, classId, sectionId, PageRequest.of(0, 100)));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine() {
        return mine(null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(Integer limit) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        UserRole role = TenantContext.getRole();
        int cap = limit != null && limit > 0 ? Math.min(limit, 200) : 80;
        PageRequest page = PageRequest.of(0, cap);
        if (role == UserRole.TEACHER) {
            return toMaps(homeworkRepository.findByTenantIdAndTeacherUserIdOrderByDueDateDesc(
                    tenantId, TenantContext.getUserId(), page));
        }
        if (role == UserRole.STUDENT) {
            Student me = studentRepository.findByTenantIdAndUserId(tenantId, TenantContext.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
            if (me.getClassId() == null || me.getSectionId() == null) {
                return List.of();
            }
            PageRequest studentPage = page == null ? PageRequest.of(0, 80) : page;
            return toMaps(homeworkRepository.findByTenantIdAndClassIdAndSectionIdOrderByDueDateDesc(
                    tenantId, me.getClassId(), me.getSectionId(), studentPage));
        }
        if (role == UserRole.SCHOOL_ADMIN || role == UserRole.PRINCIPAL || role == UserRole.ERP_OWNER) {
            return toMaps(homeworkRepository.findByTenantIdOrderByDueDateDesc(tenantId, page));
        }
        if (role == UserRole.PARENT) {
            List<UUID> linkedIds = parentStudentRepository.findByTenantIdAndParentUserId(tenantId, TenantContext.getUserId())
                    .stream()
                    .map(ParentStudent::getStudentId)
                    .toList();
            if (linkedIds.isEmpty()) {
                return List.of();
            }
            java.util.Set<String> seenSections = new java.util.HashSet<>();
            java.util.LinkedHashMap<UUID, Homework> unique = new java.util.LinkedHashMap<>();
            PageRequest parentPage = page == null ? PageRequest.of(0, 80) : page;
            for (Student st : studentRepository.findAllById(linkedIds)) {
                if (st.getClassId() == null || st.getSectionId() == null) {
                    continue;
                }
                String key = st.getClassId() + ":" + st.getSectionId();
                if (!seenSections.add(key)) {
                    continue;
                }
                for (Homework h : homeworkRepository.findByTenantIdAndClassIdAndSectionIdOrderByDueDateDesc(
                        tenantId, st.getClassId(), st.getSectionId(), parentPage)) {
                    unique.putIfAbsent(h.getId(), h);
                }
            }
            return cap(toMaps(List.copyOf(unique.values())), limit);
        }
        throw new ResourceNotFoundException("Use classId and sectionId filters");
    }

    private static List<Map<String, Object>> cap(List<Map<String, Object>> rows, Integer limit) {
        if (limit == null || limit <= 0 || rows.size() <= limit) {
            return rows;
        }
        return List.copyOf(rows.subList(0, limit));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID homeworkId) {
        Homework homework = requireHomework(homeworkId);
        UserRole role = TenantContext.getRole();
        if (role == UserRole.TEACHER && !homework.getTeacherUserId().equals(TenantContext.getUserId())) {
            throw new ForbiddenException("Teachers can only view their own homework");
        }
        if (role == UserRole.STUDENT || role == UserRole.PARENT) {
            forClass(homework.getClassId(), homework.getSectionId());
        }
        return toMap(homework);
    }

    @Transactional
    public HomeworkSubmission submit(UUID homeworkId, UUID requestedStudentId, String fileUrl, String notes) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        Homework homework = requireHomework(homeworkId);
        UUID studentId = resolveSubmitterStudentId(tenantId, requestedStudentId);
        Student student = studentAccessService.requireStudent(studentId);
        if (!homework.getClassId().equals(student.getClassId())
                || !homework.getSectionId().equals(student.getSectionId())) {
            throw new ForbiddenException("Student is not in this homework class/section");
        }
        if ((fileUrl == null || fileUrl.isBlank()) && (notes == null || notes.isBlank())) {
            throw new BusinessException("SUBMISSION_EMPTY", "Provide a file or notes for the submission");
        }
        if (submissionRepository.findByTenantIdAndHomeworkIdAndStudentId(tenantId, homeworkId, studentId).isPresent()) {
            throw new DuplicateResourceException("Homework already submitted");
        }
        HomeworkSubmission row = new HomeworkSubmission();
        row.setTenantId(tenantId);
        row.setHomeworkId(homeworkId);
        row.setStudentId(studentId);
        row.setFileUrl(fileUrl);
        row.setNotes(notes);
        row.setSubmittedAt(Instant.now());
        row.setStatus(HomeworkSubmission.statusForDueDate(homework.getDueDate(), LocalDate.now()));
        row.setCreatedBy(TenantContext.getUserId());
        HomeworkSubmission saved = submissionRepository.save(row);
        notificationService.notifyUser(tenantId, homework.getTeacherUserId(), "HOMEWORK_SUBMISSION",
                "Homework submitted: " + homework.getTitle(),
                "Student work received",
                "HomeworkSubmission", saved.getId().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<HomeworkSubmission> submissions(UUID homeworkId) {
        Homework homework = requireHomework(homeworkId);
        UserRole role = TenantContext.getRole();
        if (role == UserRole.TEACHER && !homework.getTeacherUserId().equals(TenantContext.getUserId())) {
            throw new ForbiddenException("Teachers can only view submissions for their homework");
        }
        return submissionRepository.findByTenantIdAndHomeworkIdOrderBySubmittedAtDesc(
                homework.getTenantId(), homeworkId);
    }

    @Transactional
    public HomeworkSubmission review(UUID submissionId, String remark) {
        HomeworkSubmission row = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Homework submission", submissionId));
        TenantGuard.assertSameTenant(row.getTenantId());
        Homework homework = requireHomework(row.getHomeworkId());
        UserRole role = TenantContext.getRole();
        if (role == UserRole.TEACHER && !homework.getTeacherUserId().equals(TenantContext.getUserId())) {
            throw new ForbiddenException("Teachers can only review their own homework");
        }
        row.setStatus(HomeworkSubmissionStatus.REVIEWED);
        row.setTeacherRemark(remark);
        row.setReviewedAt(Instant.now());
        row.setReviewedBy(TenantContext.getUserId());
        row.setUpdatedBy(TenantContext.getUserId());
        return submissionRepository.save(row);
    }

    private Homework requireHomework(UUID id) {
        Homework homework = homeworkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Homework", id));
        TenantGuard.assertSameTenant(homework.getTenantId());
        return homework;
    }

    private UUID resolveSubmitterStudentId(UUID tenantId, UUID requestedStudentId) {
        UserRole role = TenantContext.getRole();
        if (role == UserRole.STUDENT) {
            Student me = studentRepository.findByTenantIdAndUserId(tenantId, TenantContext.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
            if (requestedStudentId != null && !requestedStudentId.equals(me.getId())) {
                throw new ForbiddenException("Students can only submit their own homework");
            }
            return me.getId();
        }
        if (requestedStudentId == null) {
            throw new BusinessException("STUDENT_REQUIRED", "studentId is required");
        }
        studentAccessService.requireStudent(requestedStudentId);
        return requestedStudentId;
    }

    private List<Map<String, Object>> toMaps(List<Homework> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<HomeworkAttachment>> byHomework = attachmentRepository
                .findByHomeworkIdIn(list.stream().map(Homework::getId).toList())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(HomeworkAttachment::getHomeworkId));
        Map<UUID, String> classNames = academicService.classes().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.erpschool.academic.entity.SchoolClass::getId,
                        com.erpschool.academic.entity.SchoolClass::getName,
                        (a, b) -> a));
        Map<UUID, String> subjectNames = academicService.subjects().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.erpschool.academic.entity.Subject::getId,
                        com.erpschool.academic.entity.Subject::getName,
                        (a, b) -> a));
        Map<UUID, String> sectionNames = academicService.allSections().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.erpschool.academic.entity.Section::getId,
                        com.erpschool.academic.entity.Section::getName,
                        (a, b) -> a));
        return list.stream()
                .map(h -> {
                    Map<String, Object> m = toMap(h, byHomework.getOrDefault(h.getId(), List.of()));
                    m.put("className", classNames.get(h.getClassId()));
                    m.put("sectionName", sectionNames.get(h.getSectionId()));
                    m.put("subjectName", subjectNames.get(h.getSubjectId()));
                    return m;
                })
                .toList();
    }

    private Map<String, Object> toMap(Homework h) {
        return toMap(h, attachmentRepository.findByHomeworkId(h.getId()));
    }

    private Map<String, Object> toMap(Homework h, List<HomeworkAttachment> attachments) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", h.getId());
        m.put("teacherUserId", h.getTeacherUserId());
        m.put("classId", h.getClassId());
        m.put("sectionId", h.getSectionId());
        m.put("subjectId", h.getSubjectId());
        m.put("title", h.getTitle());
        m.put("description", h.getDescription());
        m.put("dueDate", h.getDueDate());
        m.put("createdAt", h.getCreatedAt());
        m.put("attachments", attachments);
        return m;
    }

    public record AttachmentIn(String fileName, String fileUrl, String contentType) {
    }
}
