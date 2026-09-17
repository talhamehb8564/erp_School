package com.erpschool.homework.service;

import com.erpschool.academic.service.AcademicService;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.homework.entity.Homework;
import com.erpschool.homework.entity.HomeworkAttachment;
import com.erpschool.homework.repository.HomeworkAttachmentRepository;
import com.erpschool.homework.repository.HomeworkRepository;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HomeworkService {

    private final HomeworkRepository homeworkRepository;
    private final HomeworkAttachmentRepository attachmentRepository;
    private final AcademicService academicService;
    private final StudentRepository studentRepository;
    private final StudentAccessService studentAccessService;
    private final ParentStudentRepository parentStudentRepository;
    private final NotificationService notificationService;

    public HomeworkService(HomeworkRepository homeworkRepository,
                           HomeworkAttachmentRepository attachmentRepository,
                           AcademicService academicService,
                           StudentRepository studentRepository,
                           StudentAccessService studentAccessService,
                           ParentStudentRepository parentStudentRepository,
                           NotificationService notificationService) {
        this.homeworkRepository = homeworkRepository;
        this.attachmentRepository = attachmentRepository;
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
        if (attachments != null) {
            for (AttachmentIn a : attachments) {
                HomeworkAttachment ha = new HomeworkAttachment();
                ha.setTenantId(tenantId);
                ha.setHomeworkId(saved.getId());
                ha.setFileName(a.fileName());
                ha.setFileUrl(a.fileUrl());
                ha.setContentType(a.contentType());
                ha.setCreatedBy(TenantContext.getUserId());
                attachmentRepository.save(ha);
            }
        }
        studentRepository.findByTenantIdAndClassIdAndSectionIdAndStatus(
                        tenantId, saved.getClassId(), saved.getSectionId(), StudentStatus.ACTIVE)
                .forEach(st -> {
                    notificationService.notifyUser(tenantId, st.getUserId(), "HOMEWORK",
                            "New homework: " + saved.getTitle(), saved.getDescription(),
                            "Homework", saved.getId().toString());
                    parentStudentRepository.findByTenantIdAndStudentId(tenantId, st.getId())
                            .forEach(link -> notificationService.notifyUser(tenantId, link.getParentUserId(),
                                    "HOMEWORK", "Homework for your child: " + saved.getTitle(),
                                    saved.getDescription(), "Homework", saved.getId().toString()));
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
        if (role == UserRole.PARENT) {
            return parentStudentRepository.findByTenantIdAndParentUserId(tenantId, TenantContext.getUserId())
                    .stream()
                    .map(ParentStudent::getStudentId)
                    .map(studentAccessService::requireStudent)
                    .flatMap(st -> homeworkRepository
                            .findByTenantIdAndClassIdAndSectionIdOrderByDueDateDesc(tenantId, st.getClassId(), st.getSectionId())
                            .stream())
                    .distinct()
                    .map(this::toMap)
                    .toList();
        }
        throw new ResourceNotFoundException("Use classId and sectionId filters");
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
