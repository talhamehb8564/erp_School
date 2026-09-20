package com.erpschool.bootstrap;

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
import com.erpschool.campus.entity.Campus;
import com.erpschool.campus.repository.CampusRepository;
import com.erpschool.common.service.DocumentSequenceService;
import com.erpschool.config.AppProperties;
import com.erpschool.fee.entity.FeeStructure;
import com.erpschool.fee.repository.FeeStructureRepository;
import com.erpschool.salary.entity.StaffProfile;
import com.erpschool.salary.repository.StaffProfileRepository;
import com.erpschool.student.entity.ParentStudent;
import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import com.erpschool.student.repository.ParentStudentRepository;
import com.erpschool.student.repository.StudentRepository;
import com.erpschool.subscription.entity.Subscription;
import com.erpschool.subscription.entity.SubscriptionStatus;
import com.erpschool.subscription.repository.SubscriptionRepository;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.entity.User;
import com.erpschool.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Slf4j
@Component
@Order(20)
public class DemoAcademicSeeder implements ApplicationRunner {

    private final AppProperties properties;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final CampusRepository campusRepository;
    private final SchoolClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final TeacherAssignmentRepository assignmentRepository;
    private final TimetableSlotRepository timetableRepository;
    private final StudentRepository studentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DocumentSequenceService documentSequenceService;

    public DemoAcademicSeeder(AppProperties properties,
                              TenantRepository tenantRepository,
                              UserRepository userRepository,
                              CampusRepository campusRepository,
                              SchoolClassRepository classRepository,
                              SectionRepository sectionRepository,
                              SubjectRepository subjectRepository,
                              ClassSubjectRepository classSubjectRepository,
                              TeacherAssignmentRepository assignmentRepository,
                              TimetableSlotRepository timetableRepository,
                              StudentRepository studentRepository,
                              ParentStudentRepository parentStudentRepository,
                              FeeStructureRepository feeStructureRepository,
                              StaffProfileRepository staffProfileRepository,
                              SubscriptionRepository subscriptionRepository,
                              DocumentSequenceService documentSequenceService) {
        this.properties = properties;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.campusRepository = campusRepository;
        this.classRepository = classRepository;
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.classSubjectRepository = classSubjectRepository;
        this.assignmentRepository = assignmentRepository;
        this.timetableRepository = timetableRepository;
        this.studentRepository = studentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.feeStructureRepository = feeStructureRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.documentSequenceService = documentSequenceService;
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
        if (!campusRepository.findByTenantIdOrderByNameAsc(tenantId).isEmpty()) {
            return;
        }

        Campus campus = new Campus();
        campus.setTenantId(tenantId);
        campus.setName("Main Campus");
        campus.setCode("MAIN");
        campus.setCity("Lahore");
        campus = campusRepository.save(campus);

        SchoolClass grade5 = new SchoolClass();
        grade5.setTenantId(tenantId);
        grade5.setCampusId(campus.getId());
        grade5.setName("Grade 5");
        grade5.setGrade("5");
        grade5.setAcademicSession("2026-2027");
        grade5 = classRepository.save(grade5);

        Section sectionA = new Section();
        sectionA.setTenantId(tenantId);
        sectionA.setClassId(grade5.getId());
        sectionA.setName("A");
        sectionA = sectionRepository.save(sectionA);

        Subject math = new Subject();
        math.setTenantId(tenantId);
        math.setName("Mathematics");
        math.setCode("MATH");
        math = subjectRepository.save(math);

        ClassSubject cs = new ClassSubject();
        cs.setTenantId(tenantId);
        cs.setClassId(grade5.getId());
        cs.setSubjectId(math.getId());
        classSubjectRepository.save(cs);

        User teacher = userRepository.findByEmailIgnoreCase("teacher@greenvalley.school").orElse(null);
        User studentUser = userRepository.findByEmailIgnoreCase("student@greenvalley.school").orElse(null);
        User parentUser = userRepository.findByEmailIgnoreCase("parent@greenvalley.school").orElse(null);

        if (teacher != null) {
            TeacherAssignment assignment = new TeacherAssignment();
            assignment.setTenantId(tenantId);
            assignment.setTeacherUserId(teacher.getId());
            assignment.setClassId(grade5.getId());
            assignment.setSectionId(sectionA.getId());
            assignment.setSubjectId(math.getId());
            assignmentRepository.save(assignment);

            TimetableSlot slot = new TimetableSlot();
            slot.setTenantId(tenantId);
            slot.setClassId(grade5.getId());
            slot.setSectionId(sectionA.getId());
            slot.setSubjectId(math.getId());
            slot.setTeacherUserId(teacher.getId());
            slot.setDayOfWeek(1);
            slot.setStartTime(LocalTime.of(8, 0));
            slot.setEndTime(LocalTime.of(8, 45));
            timetableRepository.save(slot);

            StaffProfile profile = new StaffProfile();
            profile.setTenantId(tenantId);
            profile.setUserId(teacher.getId());
            profile.setBaseSalary(new BigDecimal("50000"));
            staffProfileRepository.save(profile);
        }

        if (studentUser != null && studentRepository.findByTenantIdAndUserId(tenantId, studentUser.getId()).isEmpty()) {
            Student student = new Student();
            student.setTenantId(tenantId);
            student.setUserId(studentUser.getId());
            student.setCampusId(campus.getId());
            student.setClassId(grade5.getId());
            student.setSectionId(sectionA.getId());
            String admission = documentSequenceService.nextFormatted(
                    tenantId, "ADM", tenant.getCode().toUpperCase() + "-ADM-");
            student.setAdmissionNumber(admission);
            student.setRegistrationNumber(admission);
            student.setRollNumber("1");
            student.setStatus(StudentStatus.ACTIVE);
            student.setAdmissionDate(LocalDate.now());
            student.setGuardianName("Ahmed Khan");
            student = studentRepository.save(student);
            if (parentUser != null) {
                ParentStudent link = new ParentStudent();
                link.setTenantId(tenantId);
                link.setParentUserId(parentUser.getId());
                link.setStudentId(student.getId());
                link.setRelationship("FATHER");
                link.setPrimaryLink(true);
                parentStudentRepository.save(link);
            }
        }

        FeeStructure fees = new FeeStructure();
        fees.setTenantId(tenantId);
        fees.setClassId(grade5.getId());
        fees.setName("Grade 5 tuition");
        fees.setAcademicYear("2026-2027");
        fees.setTuitionAmount(new BigDecimal("8000"));
        feeStructureRepository.save(fees);

        if (subscriptionRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantId).isEmpty()) {
            Subscription sub = new Subscription();
            sub.setTenantId(tenantId);
            sub.setStatus(SubscriptionStatus.PAID);
            sub.setAmount(new BigDecimal("25000"));
            sub.setCurrency("PKR");
            sub.setPeriodStart(LocalDate.now().withDayOfYear(1));
            sub.setPeriodEnd(LocalDate.now().withDayOfYear(1).plusYears(1).minusDays(1));
            sub.setNotes("Demo school subscription");
            subscriptionRepository.save(sub);
        }
        log.info("Seeded demo academic data for {}", tenant.getCode());
    }
}
