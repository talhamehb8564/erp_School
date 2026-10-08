package com.erpschool.bootstrap;

import com.erpschool.academic.entity.SchoolClass;
import com.erpschool.academic.entity.Section;
import com.erpschool.campus.entity.Campus;
import com.erpschool.campus.repository.CampusRepository;
import com.erpschool.academic.repository.SchoolClassRepository;
import com.erpschool.academic.repository.SectionRepository;
import com.erpschool.common.service.DocumentSequenceService;
import com.erpschool.config.AppProperties;
import com.erpschool.exam.repository.ExamSessionRepository;
import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.repository.UserRepository;
import com.erpschool.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Ensures 3 branches (campuses), 50 students each, CNIC usernames, and an announce time.
 */
@Slf4j
@Component
@Order(30)
public class BranchIsolationSeeder implements ApplicationRunner {

    private final AppProperties properties;
    private final TenantRepository tenantRepository;
    private final CampusRepository campusRepository;
    private final SchoolClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final DocumentSequenceService documentSequenceService;
    private final ExamSessionRepository examSessionRepository;

    public BranchIsolationSeeder(AppProperties properties,
                                 TenantRepository tenantRepository,
                                 CampusRepository campusRepository,
                                 SchoolClassRepository classRepository,
                                 SectionRepository sectionRepository,
                                 StudentRepository studentRepository,
                                 UserRepository userRepository,
                                 UserService userService,
                                 DocumentSequenceService documentSequenceService,
                                 ExamSessionRepository examSessionRepository) {
        this.properties = properties;
        this.tenantRepository = tenantRepository;
        this.campusRepository = campusRepository;
        this.classRepository = classRepository;
        this.sectionRepository = sectionRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.documentSequenceService = documentSequenceService;
        this.examSessionRepository = examSessionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.getSeed().isEnabled()) {
            return;
        }
        Tenant tenant = tenantRepository.findByCodeIgnoreCase(properties.getSeed().getDemoSchoolCode()).orElse(null);
        if (tenant == null) {
            return;
        }
        UUID tenantId = tenant.getId();
        String password = properties.getSeed().getDemoUserPassword();
        Campus main = ensureCampus(tenantId, "MAIN", "Main Campus");
        Campus canal = ensureCampus(tenantId, "CANAL", "Canal Campus");
        Campus cantt = ensureCampus(tenantId, "CANTT", "Cantt Campus");

        SchoolClass playgroup = ensureClass(tenantId, main.getId(), "Playgroup", "PG");
        SchoolClass grade1 = ensureClass(tenantId, main.getId(), "Grade 1", "1");
        SchoolClass grade7 = ensureClass(tenantId, cantt.getId(), "Grade 7", "7");
        ensureSection(tenantId, playgroup.getId(), "A");
        ensureSection(tenantId, grade1.getId(), "A");
        Section g7a = ensureSection(tenantId, grade7.getId(), "A");

        pinUserCampus("admin@greenvalley.school", main.getId());
        pinUserCampus("principal@greenvalley.school", main.getId());
        pinUserCampus("teacher@greenvalley.school", main.getId());
        pinUserCampus("accounts@greenvalley.school", main.getId());
        pinUserCampus("parent@greenvalley.school", main.getId());
        pinUserCampus("student@greenvalley.school", main.getId());

        ensureBranchStaff(tenant, canal, "canal", password);
        ensureBranchStaff(tenant, cantt, "cantt", password);

        fillCampus(tenant, main, playgroup, ensureSection(tenantId, playgroup.getId(), "A"), password, 50);
        List<SchoolClass> canalClasses = classRepository.findByTenantIdAndCampusIdOrderByNameAsc(tenantId, canal.getId());
        if (!canalClasses.isEmpty()) {
            SchoolClass cc = canalClasses.get(0);
            List<Section> secs = sectionRepository.findByTenantIdAndClassIdOrderByNameAsc(tenantId, cc.getId());
            if (!secs.isEmpty()) {
                fillCampus(tenant, canal, cc, secs.get(0), password, 50);
            }
        }
        fillCampus(tenant, cantt, grade7, g7a, password, 50);

        backfillCnics(tenantId);
        examSessionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().findFirst().ifPresent(session -> {
            if (session.getAnnounceAt() == null) {
                session.setAnnounceAt(Instant.now().minus(1, ChronoUnit.HOURS));
                examSessionRepository.save(session);
            }
        });
        log.info("Branch isolation seed complete for {}: campuses={}", tenant.getCode(),
                campusRepository.findByTenantIdOrderByNameAsc(tenantId).size());
    }

    private Campus ensureCampus(UUID tenantId, String code, String name) {
        return campusRepository.findByTenantIdAndCodeIgnoreCase(tenantId, code).orElseGet(() -> {
            Campus campus = new Campus();
            campus.setTenantId(tenantId);
            campus.setCode(code);
            campus.setName(name);
            campus.setCity("Lahore");
            return campusRepository.save(campus);
        });
    }

    private SchoolClass ensureClass(UUID tenantId, UUID campusId, String name, String grade) {
        return classRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .filter(c -> name.equalsIgnoreCase(c.getName()))
                .findFirst()
                .orElseGet(() -> {
                    SchoolClass clazz = new SchoolClass();
                    clazz.setTenantId(tenantId);
                    clazz.setCampusId(campusId);
                    clazz.setName(name);
                    clazz.setGrade(grade);
                    clazz.setAcademicSession("2026-2027");
                    return classRepository.save(clazz);
                });
    }

    private Section ensureSection(UUID tenantId, UUID classId, String name) {
        return sectionRepository.findByTenantIdAndClassIdOrderByNameAsc(tenantId, classId).stream()
                .filter(s -> name.equalsIgnoreCase(s.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Section section = new Section();
                    section.setTenantId(tenantId);
                    section.setClassId(classId);
                    section.setName(name);
                    return sectionRepository.save(section);
                });
    }

    private void pinUserCampus(String email, UUID campusId) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            user.setCampusId(campusId);
            userRepository.save(user);
        });
    }

    private void ensureBranchStaff(Tenant tenant, Campus campus, String slug, String password) {
        String domain = slug + "@greenvalley.school";
        User admin = ensureUser(tenant, UserRole.SCHOOL_ADMIN, "Branch", slug, "admin." + domain, password);
        admin.setCampusId(campus.getId());
        userRepository.save(admin);
        User principal = ensureUser(tenant, UserRole.PRINCIPAL, "Principal", slug, "principal." + domain, password);
        principal.setCampusId(campus.getId());
        userRepository.save(principal);
        User ao = ensureUser(tenant, UserRole.ACCOUNT_OFFICER, "Accounts", slug, "accounts." + domain, password);
        ao.setCampusId(campus.getId());
        userRepository.save(ao);
        User teacher = ensureUser(tenant, UserRole.TEACHER, "Teacher", slug, "teacher." + domain, password);
        teacher.setCampusId(campus.getId());
        userRepository.save(teacher);
    }

    private User ensureUser(Tenant tenant, UserRole role, String first, String last, String email, String password) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() ->
                userRepository.findById(userService.createInternal(
                        tenant.getId(), tenant.getCode(), role, first, last, email, null, password, false
                ).getUser().getId()).orElseThrow());
    }

    private void fillCampus(Tenant tenant, Campus campus, SchoolClass clazz, Section section, String password, int target) {
        UUID tenantId = tenant.getId();
        int safety = 0;
        int seq = (int) studentRepository.countByTenantId(tenantId) + 1;
        while (studentRepository.countByTenantIdAndCampusId(tenantId, campus.getId()) < target && safety++ < 80) {
            String email = "stu." + campus.getCode().toLowerCase() + seq + "@greenvalley.school";
            seq++;
            if (userRepository.existsByEmailIgnoreCase(email)) {
                continue;
            }
            User account = userRepository.findById(userService.createInternal(
                    tenantId, tenant.getCode(), UserRole.STUDENT,
                    "Student", campus.getCode() + seq, email, null, password, false
            ).getUser().getId()).orElseThrow();
            String cnic = String.format("3%02d%010d", Math.abs(campus.getCode().hashCode()) % 90, seq);
            account.setUsername(cnic);
            account.setCampusId(campus.getId());
            userRepository.save(account);
            Student student = new Student();
            student.setTenantId(tenantId);
            student.setUserId(account.getId());
            student.setCampusId(campus.getId());
            student.setClassId(clazz.getId());
            student.setSectionId(section.getId());
            student.setAdmissionNumber(documentSequenceService.nextFormatted(
                    tenantId, "ADM", tenant.getCode().toUpperCase() + "-ADM-"));
            student.setRollNumber(String.valueOf(seq));
            student.setCnic(cnic);
            student.setStatus(StudentStatus.ACTIVE);
            student.setAdmissionDate(LocalDate.of(2026, 4, 1));
            studentRepository.save(student);
        }
    }

    private void backfillCnics(UUID tenantId) {
        int n = 1;
        for (Student student : studentRepository.findByTenantId(tenantId)) {
            if (student.getCnic() != null) {
                continue;
            }
            String cnic = String.format("35201%07d1", n++);
            student.setCnic(cnic);
            studentRepository.save(student);
            if (student.getUserId() != null) {
                String username = cnic;
                userRepository.findById(student.getUserId()).ifPresent(account -> {
                    if (account.getRole() == UserRole.STUDENT) {
                        userRepository.findByUsernameIgnoreCase(username)
                                .filter(existing -> !existing.getId().equals(account.getId()))
                                .ifPresentOrElse(existing -> { /* keep generated username */ }, () -> {
                                    account.setUsername(username);
                                    userRepository.save(account);
                                });
                    }
                });
            }
        }
    }
}
