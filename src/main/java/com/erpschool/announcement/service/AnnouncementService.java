package com.erpschool.announcement.service;

import com.erpschool.announcement.entity.Announcement;
import com.erpschool.announcement.repository.AnnouncementRepository;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.notification.service.NotificationService;
import com.erpschool.student.entity.Student;
import com.erpschool.student.repository.ParentStudentRepository;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AnnouncementService {

    private final AnnouncementRepository repository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final NotificationService notificationService;

    public AnnouncementService(AnnouncementRepository repository,
                               UserRepository userRepository,
                               StudentRepository studentRepository,
                               ParentStudentRepository parentStudentRepository,
                               NotificationService notificationService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Announcement create(Announcement a) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        a.setTenantId(tenantId);
        if (a.getPublishDate() == null) {
            a.setPublishDate(LocalDate.now());
        }
        a.setCreatedBy(TenantContext.getUserId());
        Announcement saved = repository.save(a);
        fanout(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Announcement> list() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        UserRole role = TenantContext.getRole();
        LocalDate today = LocalDate.now();
        return repository.findByTenantIdOrderByPublishDateDesc(tenantId).stream()
                .filter(a -> !a.getPublishDate().isAfter(today))
                .filter(a -> a.getExpiryDate() == null || !a.getExpiryDate().isBefore(today))
                .filter(a -> visibleTo(a, role))
                .toList();
    }

    private boolean visibleTo(Announcement a, UserRole role) {
        return switch (a.getAudience()) {
            case "ALL" -> true;
            case "TEACHERS" -> role == UserRole.TEACHER || isStaff(role);
            case "PARENTS" -> role == UserRole.PARENT || isStaff(role);
            case "STUDENTS" -> role == UserRole.STUDENT || isStaff(role);
            case "CLASS", "SECTION" -> isStaff(role) || role == UserRole.STUDENT || role == UserRole.PARENT;
            default -> isStaff(role);
        };
    }

    private boolean isStaff(UserRole role) {
        return role == UserRole.ERP_OWNER || role == UserRole.SCHOOL_ADMIN
                || role == UserRole.PRINCIPAL || role == UserRole.ACCOUNT_OFFICER || role == UserRole.TEACHER;
    }

    private void fanout(Announcement a) {
        UUID tenantId = a.getTenantId();
        List<User> targets;
        switch (a.getAudience()) {
            case "TEACHERS" -> targets = userRepository.findByTenantIdAndRole(tenantId, UserRole.TEACHER);
            case "PARENTS" -> targets = userRepository.findByTenantIdAndRole(tenantId, UserRole.PARENT);
            case "STUDENTS" -> targets = userRepository.findByTenantIdAndRole(tenantId, UserRole.STUDENT);
            case "CLASS" -> targets = studentRepository.findByTenantId(tenantId, org.springframework.data.domain.Pageable.unpaged())
                    .stream()
                    .filter(s -> a.getClassId() != null && a.getClassId().equals(s.getClassId()))
                    .map(Student::getUserId)
                    .filter(id -> id != null)
                    .map(id -> userRepository.findById(id).orElse(null))
                    .filter(u -> u != null)
                    .toList();
            default -> targets = List.of();
        }
        for (User u : targets) {
            notificationService.notifyUser(tenantId, u.getId(), "ANNOUNCEMENT", a.getTitle(), a.getBody(),
                    "Announcement", a.getId().toString());
        }
        if ("CLASS".equals(a.getAudience()) || "SECTION".equals(a.getAudience()) || "PARENTS".equals(a.getAudience())
                || "ALL".equals(a.getAudience())) {
            // parents of class students
            if (a.getClassId() != null) {
                studentRepository.findByTenantId(tenantId, org.springframework.data.domain.Pageable.unpaged())
                        .stream()
                        .filter(s -> a.getClassId().equals(s.getClassId()))
                        .filter(s -> a.getSectionId() == null || a.getSectionId().equals(s.getSectionId()))
                        .forEach(s -> parentStudentRepository.findByTenantIdAndStudentId(tenantId, s.getId())
                                .forEach(link -> notificationService.notifyUser(tenantId, link.getParentUserId(),
                                        "ANNOUNCEMENT", a.getTitle(), a.getBody(),
                                        "Announcement", a.getId().toString())));
            }
        }
    }
}
