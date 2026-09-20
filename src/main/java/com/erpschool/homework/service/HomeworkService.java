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
        UserRole role = TenantContext.getRole();
        if (role == UserRole.STUDENT) {
            Student me = studentRepository.findByTenantIdAndUserId(tenantId, TenantContext.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
            if (!classId.equals(me.getClassId()) || !sectionId.equals(me.getSectionId())) {
                throw new com.erpschool.common.exception.ForbiddenException("Cannot view another class homework");
            }
        }
        if (role == UserRole.PARENT) {
            boolean linked = parentStudentRepository.findByTenantIdAndParentUserId(tenantId, TenantContext.getUserId())
                    .stream()
                    .map(ParentStudent::getStudentId)
                    .map(id -> studentRepository.findById(id).orElse(null))
                    .filter(s -> s != null)
                    .anyMatch(s -> classId.equals(s.getClassId()) && sectionId.equals(s.getSectionId()));
            if (!linked) {
                throw new com.erpschool.common.exception.ForbiddenException("Cannot view homework for unrelated classes");
            }
        }
        return homeworkRepository.findByTenantIdAndClassIdAndSectionIdOrderByDueDateDesc(tenantId, classId, sectionId)
                .stream().map(this::toMap).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        UserRole role = TenantContext.getRole();
        if (role == UserRole.TEACHER) {
            return homeworkRepository.findByTenantIdAndTeacherUserIdOrderByDueDateDesc(tenantId, TenantContext.getUserId())
                    .stream().map(this::toMap).toList();
        }
        if (role == UserRole.STUDENT) {
            Student me = studentRepository.findByTenantIdAndUserId(tenantId, TenantContext.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
            return forClass(me.getClassId(), me.getSectionId());
        }
        if (role == UserRole.SCHOOL_ADMIN || role == UserRole.PRINCIPAL || role == UserRole.ERP_OWNER) {
            return homeworkRepository.findByTenantIdOrderByDueDateDesc(tenantId)
                    .stream().map(this::toMap).toList();
        }
        if (role == UserRole.PARENT) {
            return parentStudentRepository.findByTenantIdAndParentUserId(tenantId, TenantContext.getUserId())
                    .stream()
                    .map(ParentStudent::getStudentId)
                    .map(studentAccessService::requireStudent)
                    .flatMap(st -> homeworkRepository
                            .findByTenantIdAndClassIdAndSectionIdOrderByDueDateDesc(tenantId, st.getClassId(), st.getSectionId())
                            .stream())
                    .collect(java.util.stream.Collectors.toMap(
                            Homework::getId, h -> h, (a, b) -> a, java.util.LinkedHashMap::new))
                    .values()
                    .stream()
                    .map(this::toMap)
                    .toList();
        }
        throw new ResourceNotFoundException("Use classId and sectionId filters");
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

    private Map<String, Object> toMap(Homework h) {
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
        m.put("attachments", attachmentRepository.findByHomeworkId(h.getId()));
        return m;
    }

    public record AttachmentIn(String fileName, String fileUrl, String contentType) {
    }
}
