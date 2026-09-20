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
import com.erpschool.announcement.entity.Announcement;
import com.erpschool.announcement.repository.AnnouncementRepository;
import com.erpschool.attendance.entity.AttendanceStatus;
import com.erpschool.attendance.entity.StudentAttendance;
import com.erpschool.attendance.repository.StudentAttendanceRepository;
import com.erpschool.calendar.entity.SchoolEvent;
import com.erpschool.calendar.repository.SchoolEventRepository;
import com.erpschool.campus.entity.Campus;
import com.erpschool.campus.repository.CampusRepository;
import com.erpschool.common.service.DocumentSequenceService;
import com.erpschool.common.util.GradeCalculator;
import com.erpschool.config.AppProperties;
import com.erpschool.exam.entity.ExamResult;
import com.erpschool.exam.entity.ExamSession;
import com.erpschool.exam.repository.ExamResultRepository;
import com.erpschool.exam.repository.ExamSessionRepository;
import com.erpschool.fee.entity.ChallanStatus;
import com.erpschool.fee.entity.FeeChallan;
import com.erpschool.fee.entity.FeeStructure;
import com.erpschool.fee.repository.FeeChallanRepository;
import com.erpschool.fee.repository.FeeStructureRepository;
import com.erpschool.homework.entity.Homework;
import com.erpschool.homework.repository.HomeworkRepository;
import com.erpschool.salary.entity.StaffProfile;
import com.erpschool.salary.repository.StaffProfileRepository;
import com.erpschool.settings.entity.SchoolSettings;
import com.erpschool.settings.repository.SchoolSettingsRepository;
import com.erpschool.student.entity.ParentStudent;
import com.erpschool.student.entity.Student;
import com.erpschool.student.entity.StudentStatus;
import com.erpschool.student.repository.ParentStudentRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Additive, idempotent demo population for Green Valley (GVS).
 * Fills 2 campuses, ~50 students, 14 teachers, published mid-term results,
 * homework, attendance, fees, announcements and calendar — without rewriting
 * {@link DemoAcademicSeeder}.
 */
@Slf4j
@Component
@Order(25)
public class DemoSchoolPopulationSeeder implements ApplicationRunner {

    private static final String[][] EXTRA_TEACHERS = {
            {"Sara", "Ahmed"}, {"Bilal", "Hussain"}, {"Fatima", "Noor"}, {"Usman", "Ali"},
            {"Hina", "Sheikh"}, {"Omar", "Farooq"}, {"Zara", "Malik"}, {"Imran", "Qureshi"},
            {"Kamran", "Shah"}, {"Amina", "Iqbal"}, {"Tariq", "Mehmood"}, {"Sana", "Javed"},
            {"Nida", "Butt"}
    };
    private static final String[][] EXTRA_PARENTS = {
            {"Rashid", "Ali"}, {"Saima", "Hussain"}, {"Tariq", "Sheikh"}, {"Bushra", "Malik"},
            {"Imtiaz", "Qureshi"}, {"Farah", "Ahmed"}, {"Nadeem", "Butt"}, {"Shazia", "Iqbal"},
            {"Javed", "Shah"}, {"Lubna", "Raza"}, {"Asif", "Baig"}, {"Hina", "Noor"},
            {"Waseem", "Khan"}, {"Sadia", "Farooq"}, {"Khalid", "Mehmood"}, {"Ayesha", "Javed"},
            {"Shahid", "Anwar"}, {"Naila", "Siddiqui"}, {"Adnan", "Chaudhry"}, {"Samina", "Yousaf"},
            {"Faisal", "Rehman"}, {"Rabia", "Aslam"}, {"Haroon", "Zafar"}, {"Mehwish", "Nawaz"}
    };
    private static final String[] STU_FIRST = {
            "Zain", "Ayesha", "Bilal", "Hira", "Omar", "Sana", "Hamza", "Iqra", "Usman", "Noor",
            "Daniyal", "Mariam", "Farhan", "Laiba", "Shahzaib", "Areeba", "Taha", "Maham", "Arham", "Eman",
            "Rehan", "Alina", "Saad", "Zoya", "Haris", "Mehak", "Yousuf", "Amna", "Noman", "Hafsa",
            "Kashif", "Rida", "Adeel", "Anaya", "Waleed", "Sundas", "Fawad", "Kinza", "Junaid", "Aiman",
            "Shahid", "Hoorain", "Asad", "Zara", "Imran", "Dua", "Naveed", "Saba", "Faizan"
    };
    private static final String[] STU_LAST = {
            "Ahmed", "Malik", "Hussain", "Sheikh", "Butt", "Iqbal", "Qureshi", "Raza", "Shah", "Baig"
    };
    private static final String[][] SUBJECTS = {
            {"Mathematics", "MATH"}, {"English", "ENG"}, {"Science", "SCI"},
            {"Urdu", "URD"}, {"Islamiat", "ISL"}
    };

    private final AppProperties properties;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserService userService;
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
    private final FeeChallanRepository feeChallanRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final HomeworkRepository homeworkRepository;
    private final AnnouncementRepository announcementRepository;
    private final SchoolEventRepository schoolEventRepository;
    private final ExamSessionRepository examSessionRepository;
    private final ExamResultRepository examResultRepository;
    private final StudentAttendanceRepository studentAttendanceRepository;
    private final SchoolSettingsRepository schoolSettingsRepository;
    private final DocumentSequenceService documentSequenceService;

    public DemoSchoolPopulationSeeder(AppProperties properties,
                                      TenantRepository tenantRepository,
                                      UserRepository userRepository,
                                      UserService userService,
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
                                      FeeChallanRepository feeChallanRepository,
                                      StaffProfileRepository staffProfileRepository,
                                      HomeworkRepository homeworkRepository,
                                      AnnouncementRepository announcementRepository,
                                      SchoolEventRepository schoolEventRepository,
                                      ExamSessionRepository examSessionRepository,
                                      ExamResultRepository examResultRepository,
                                      StudentAttendanceRepository studentAttendanceRepository,
                                      SchoolSettingsRepository schoolSettingsRepository,
                                      DocumentSequenceService documentSequenceService) {
        this.properties = properties;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.userService = userService;
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
        this.feeChallanRepository = feeChallanRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.homeworkRepository = homeworkRepository;
        this.announcementRepository = announcementRepository;
        this.schoolEventRepository = schoolEventRepository;
        this.examSessionRepository = examSessionRepository;
        this.examResultRepository = examResultRepository;
        this.studentAttendanceRepository = studentAttendanceRepository;
        this.schoolSettingsRepository = schoolSettingsRepository;
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
        String code = tenant.getCode();
        String password = properties.getSeed().getDemoUserPassword();

        Campus main = ensureCampus(tenantId, "MAIN", "Main Campus", "Model Town, Lahore");
        Campus canal = ensureCampus(tenantId, "CANAL", "Canal Campus", "Canal Road, Lahore");

        SchoolClass grade5 = ensureClass(tenantId, main.getId(), "Grade 5", "5");
        SchoolClass grade6 = ensureClass(tenantId, canal.getId(), "Grade 6", "6");
        Section g5a = ensureSection(tenantId, grade5.getId(), "A");
        Section g5b = ensureSection(tenantId, grade5.getId(), "B");
        Section g6a = ensureSection(tenantId, grade6.getId(), "A");
        Section g6b = ensureSection(tenantId, grade6.getId(), "B");

        List<Subject> subjects = ensureSubjects(tenantId);
        for (SchoolClass clazz : List.of(grade5, grade6)) {
            for (Subject subject : subjects) {
                if (!classSubjectRepository.existsByTenantIdAndClassIdAndSubjectId(tenantId, clazz.getId(), subject.getId())) {
                    ClassSubject link = new ClassSubject();
                    link.setTenantId(tenantId);
                    link.setClassId(clazz.getId());
                    link.setSubjectId(subject.getId());
                    classSubjectRepository.save(link);
                }
            }
            if (feeStructureRepository.findFirstByTenantIdAndClassId(tenantId, clazz.getId()).isEmpty()) {
                FeeStructure fees = new FeeStructure();
                fees.setTenantId(tenantId);
                fees.setClassId(clazz.getId());
                fees.setName(clazz.getName() + " tuition");
                fees.setAcademicYear("2026-2027");
                fees.setTuitionAmount(new BigDecimal(clazz == grade5 ? "8000" : "8500"));
                feeStructureRepository.save(fees);
            }
        }

        List<User> teachers = ensureTeachers(tenantId, code, password);
        ensureStaffProfiles(tenantId, teachers);

        userRepository.findByEmailIgnoreCase("principal@greenvalley.school").ifPresent(p -> ensureStaffProfile(tenantId, p.getId(), "90000"));
        userRepository.findByEmailIgnoreCase("accounts@greenvalley.school").ifPresent(a -> ensureStaffProfile(tenantId, a.getId(), "70000"));
        userRepository.findByEmailIgnoreCase("admin@greenvalley.school").ifPresent(a -> ensureStaffProfile(tenantId, a.getId(), "110000"));

        List<Place> places = List.of(
                new Place(main, grade5, g5a),
                new Place(main, grade5, g5b),
                new Place(canal, grade6, g6a),
                new Place(canal, grade6, g6b)
        );
        assignTeachersAndTimetable(tenantId, teachers, subjects, places);

        List<User> parents = ensureParents(tenantId, code, password);
        List<Student> students = ensureStudents(tenant, parents, places, password);

        seedHomework(tenantId, teachers, subjects, places);
        seedAnnouncements(tenantId, grade5.getId());
        seedCalendar(tenantId);
        seedAttendance(tenantId, students, places);
        seedFees(tenantId, students);
        seedPublishedResults(tenantId, students, subjects);
        seedSettings(tenantId);

        log.info("Demo population ready for {}: campuses={}, students={}, teachers={}",
                code,
                campusRepository.findByTenantIdOrderByNameAsc(tenantId).size(),
                studentRepository.countByTenantId(tenantId),
                userRepository.countByTenantIdAndRole(tenantId, UserRole.TEACHER));
    }

    private record Place(Campus campus, SchoolClass clazz, Section section) {}

    private Campus ensureCampus(UUID tenantId, String campusCode, String name, String address) {
        return campusRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .filter(c -> campusCode.equalsIgnoreCase(c.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    Campus campus = new Campus();
                    campus.setTenantId(tenantId);
                    campus.setName(name);
                    campus.setCode(campusCode);
                    campus.setCity("Lahore");
                    campus.setAddress(address);
                    campus.setPhone("+92-42-111000111");
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

    private List<Subject> ensureSubjects(UUID tenantId) {
        for (String[] row : SUBJECTS) {
            if (!subjectRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, row[1])) {
                Subject subject = new Subject();
                subject.setTenantId(tenantId);
                subject.setName(row[0]);
                subject.setCode(row[1]);
                subjectRepository.save(subject);
            }
        }
        return subjectRepository.findByTenantIdOrderByNameAsc(tenantId);
    }

    private List<User> ensureTeachers(UUID tenantId, String tenantCode, String password) {
        for (int i = 0; i < EXTRA_TEACHERS.length; i++) {
            String email = "teacher" + String.format("%02d", i + 2) + "@greenvalley.school";
            ensureUser(tenantId, tenantCode, UserRole.TEACHER, EXTRA_TEACHERS[i][0], EXTRA_TEACHERS[i][1], email, password);
        }
        return userRepository.findByTenantIdAndRole(tenantId, UserRole.TEACHER);
    }

    private List<User> ensureParents(UUID tenantId, String tenantCode, String password) {
        User demo = userRepository.findByEmailIgnoreCase("parent@greenvalley.school").orElse(null);
        List<User> parents = new ArrayList<>();
        if (demo != null) {
            parents.add(demo);
        }
        for (int i = 0; i < EXTRA_PARENTS.length; i++) {
            String email = "parent" + String.format("%02d", i + 2) + "@greenvalley.school";
            parents.add(ensureUser(tenantId, tenantCode, UserRole.PARENT,
                    EXTRA_PARENTS[i][0], EXTRA_PARENTS[i][1], email, password));
        }
        return parents;
    }

    private User ensureUser(UUID tenantId, String tenantCode, UserRole role,
                            String first, String last, String email, String password) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            userService.createInternal(tenantId, tenantCode, role, first, last, email, null, password, false);
            return userRepository.findByEmailIgnoreCase(email).orElseThrow();
        });
    }

    private void ensureStaffProfiles(UUID tenantId, List<User> teachers) {
        int i = 0;
        for (User teacher : teachers) {
            ensureStaffProfile(tenantId, teacher.getId(), String.valueOf(45000 + (i++ % 8) * 2500));
        }
    }

    private void ensureStaffProfile(UUID tenantId, UUID userId, String salary) {
        if (staffProfileRepository.findByTenantIdAndUserId(tenantId, userId).isPresent()) {
            return;
        }
        StaffProfile profile = new StaffProfile();
        profile.setTenantId(tenantId);
        profile.setUserId(userId);
        profile.setBaseSalary(new BigDecimal(salary));
        staffProfileRepository.save(profile);
    }

    private void assignTeachersAndTimetable(UUID tenantId, List<User> teachers, List<Subject> subjects, List<Place> places) {
        if (teachers.isEmpty() || subjects.isEmpty()) {
            return;
        }
        int t = 0;
        for (Place place : places) {
            for (int s = 0; s < subjects.size(); s++) {
                User teacher = teachers.get((t++) % teachers.size());
                Subject subject = subjects.get(s);
                if (!assignmentRepository.existsByTenantIdAndTeacherUserIdAndClassIdAndSectionIdAndSubjectId(
                        tenantId, teacher.getId(), place.clazz.getId(), place.section.getId(), subject.getId())) {
                    TeacherAssignment assignment = new TeacherAssignment();
                    assignment.setTenantId(tenantId);
                    assignment.setTeacherUserId(teacher.getId());
                    assignment.setClassId(place.clazz.getId());
                    assignment.setSectionId(place.section.getId());
                    assignment.setSubjectId(subject.getId());
                    assignmentRepository.save(assignment);
                }
            }
            List<TimetableSlot> existing = timetableRepository
                    .findByTenantIdAndClassIdAndSectionIdOrderByDayOfWeekAscStartTimeAsc(
                            tenantId, place.clazz.getId(), place.section.getId());
            if (!existing.isEmpty()) {
                continue;
            }
            for (int day = 1; day <= 5; day++) {
                Subject subject = subjects.get((day - 1) % subjects.size());
                User teacher = teachers.get((day + places.indexOf(place)) % teachers.size());
                TimetableSlot slot = new TimetableSlot();
                slot.setTenantId(tenantId);
                slot.setClassId(place.clazz.getId());
                slot.setSectionId(place.section.getId());
                slot.setSubjectId(subject.getId());
                slot.setTeacherUserId(teacher.getId());
                slot.setDayOfWeek(day);
                slot.setStartTime(LocalTime.of(8, 0));
                slot.setEndTime(LocalTime.of(8, 45));
                timetableRepository.save(slot);
            }
        }
    }

    private List<Student> ensureStudents(Tenant tenant, List<User> parents,
                                         List<Place> places, String password) {
        UUID tenantId = tenant.getId();
        Set<String> usedRolls = new HashSet<>();
        for (Student existing : studentRepository.findByTenantId(tenantId)) {
            if (existing.getRollNumber() != null) {
                usedRolls.add(existing.getRollNumber());
            }
        }
        int nextRoll = 1;
        int safety = 0;
        while (studentRepository.countByTenantId(tenantId) < 50 && safety++ < 80) {
            while (usedRolls.contains(String.valueOf(nextRoll))) {
                nextRoll++;
            }
            Place place = places.get((int) studentRepository.countByTenantId(tenantId) % places.size());
            String email = "student" + String.format("%02d", nextRoll) + "@greenvalley.school";
            if (nextRoll == 1) {
                usedRolls.add("1");
                nextRoll++;
                continue;
            }
            User account = ensureUser(tenantId, tenant.getCode(), UserRole.STUDENT,
                    STU_FIRST[(nextRoll - 2) % STU_FIRST.length],
                    STU_LAST[(nextRoll - 2) % STU_LAST.length],
                    email, password);
            if (studentRepository.findByTenantIdAndUserId(tenantId, account.getId()).isPresent()) {
                usedRolls.add(String.valueOf(nextRoll));
                nextRoll++;
                continue;
            }
            Student student = new Student();
            student.setTenantId(tenantId);
            student.setUserId(account.getId());
            student.setCampusId(place.campus.getId());
            student.setClassId(place.clazz.getId());
            student.setSectionId(place.section.getId());
            String admission = documentSequenceService.nextFormatted(
                    tenantId, "ADM", tenant.getCode().toUpperCase() + "-ADM-");
            student.setAdmissionNumber(admission);
            student.setRegistrationNumber(admission);
            student.setRollNumber(String.valueOf(nextRoll));
            student.setStatus(StudentStatus.ACTIVE);
            student.setAdmissionDate(LocalDate.of(2026, 4, 1));
            student.setGender(nextRoll % 2 == 0 ? "FEMALE" : "MALE");
            User parent = parents.get((nextRoll - 1) / 2 % parents.size());
            student.setGuardianName(parent.getFirstName() + " " + parent.getLastName());
            student.setAddress("Lahore");
            student = studentRepository.save(student);
            if (!parentStudentRepository.existsByTenantIdAndParentUserIdAndStudentId(tenantId, parent.getId(), student.getId())) {
                ParentStudent link = new ParentStudent();
                link.setTenantId(tenantId);
                link.setParentUserId(parent.getId());
                link.setStudentId(student.getId());
                link.setRelationship("GUARDIAN");
                link.setPrimaryLink(true);
                parentStudentRepository.save(link);
            }
            usedRolls.add(String.valueOf(nextRoll));
            nextRoll++;
        }
        User demoParent = userRepository.findByEmailIgnoreCase("parent@greenvalley.school").orElse(null);
        if (demoParent != null) {
            List<Student> all = studentRepository.findByTenantId(tenantId);
            int linked = 0;
            for (Student student : all) {
                if (linked >= 2) {
                    break;
                }
                if (!parentStudentRepository.existsByTenantIdAndParentUserIdAndStudentId(
                        tenantId, demoParent.getId(), student.getId())) {
                    ParentStudent link = new ParentStudent();
                    link.setTenantId(tenantId);
                    link.setParentUserId(demoParent.getId());
                    link.setStudentId(student.getId());
                    link.setRelationship("FATHER");
                    link.setPrimaryLink(linked == 0);
                    parentStudentRepository.save(link);
                }
                linked++;
            }
        }
        return studentRepository.findByTenantId(tenantId);
    }

    private void seedHomework(UUID tenantId, List<User> teachers, List<Subject> subjects, List<Place> places) {
        if (!homeworkRepository.findByTenantIdOrderByDueDateDesc(tenantId).isEmpty()
                || teachers.isEmpty() || subjects.isEmpty() || places.isEmpty()) {
            return;
        }
        Place place = places.get(0);
        Homework hw = new Homework();
        hw.setTenantId(tenantId);
        hw.setTeacherUserId(teachers.get(0).getId());
        hw.setClassId(place.clazz.getId());
        hw.setSectionId(place.section.getId());
        hw.setSubjectId(subjects.get(0).getId());
        hw.setTitle("Chapter 4 exercises");
        hw.setDescription("Complete textbook exercises 1–12. Show working.");
        hw.setDueDate(LocalDate.now().plusDays(5));
        homeworkRepository.save(hw);

        Homework hw2 = new Homework();
        hw2.setTenantId(tenantId);
        hw2.setTeacherUserId(teachers.get(0).getId());
        hw2.setClassId(place.clazz.getId());
        hw2.setSectionId(place.section.getId());
        hw2.setSubjectId(subjects.size() > 1 ? subjects.get(1).getId() : subjects.get(0).getId());
        hw2.setTitle("Reading comprehension");
        hw2.setDescription("Read the assigned passage and answer the questions.");
        hw2.setDueDate(LocalDate.now().plusDays(3));
        homeworkRepository.save(hw2);
    }

    private void seedAnnouncements(UUID tenantId, UUID classId) {
        if (!announcementRepository.findByTenantIdOrderByPublishDateDesc(tenantId).isEmpty()) {
            return;
        }
        Announcement all = new Announcement();
        all.setTenantId(tenantId);
        all.setTitle("Mid-term results published");
        all.setBody("Mid-Term 2026 results are now available. Search by roll number on Marks & results.");
        all.setAudience("ALL");
        all.setPublishDate(LocalDate.now().minusDays(1));
        announcementRepository.save(all);

        Announcement parents = new Announcement();
        parents.setTenantId(tenantId);
        parents.setTitle("Fee challans for this month");
        parents.setBody("Please pay tuition by the due date or upload a payment proof from the Fees page.");
        parents.setAudience("PARENTS");
        parents.setClassId(classId);
        parents.setPublishDate(LocalDate.now());
        announcementRepository.save(parents);
    }

    private void seedCalendar(UUID tenantId) {
        if (!schoolEventRepository.findByTenantIdOrderByStartDateAsc(tenantId).isEmpty()) {
            return;
        }
        SchoolEvent ptm = new SchoolEvent();
        ptm.setTenantId(tenantId);
        ptm.setTitle("Parent-teacher meeting");
        ptm.setDescription("Discuss mid-term progress.");
        ptm.setEventType("PTM");
        ptm.setStartDate(LocalDate.now().plusDays(10));
        ptm.setAudience("ALL");
        schoolEventRepository.save(ptm);

        SchoolEvent holiday = new SchoolEvent();
        holiday.setTenantId(tenantId);
        holiday.setTitle("Independence Day");
        holiday.setEventType("HOLIDAY");
        holiday.setStartDate(LocalDate.of(LocalDate.now().getYear(), 8, 14));
        holiday.setAudience("ALL");
        schoolEventRepository.save(holiday);
    }

    private void seedAttendance(UUID tenantId, List<Student> students, List<Place> places) {
        if (studentAttendanceRepository.countByTenantId(tenantId) > 0 || students.isEmpty() || places.isEmpty()) {
            return;
        }
        Place place = places.get(0);
        List<TimetableSlot> slots = timetableRepository
                .findByTenantIdAndClassIdAndSectionIdOrderByDayOfWeekAscStartTimeAsc(
                        tenantId, place.clazz.getId(), place.section.getId());
        if (slots.isEmpty()) {
            return;
        }
        TimetableSlot slot = slots.get(0);
        List<Student> roster = students.stream()
                .filter(s -> place.clazz.getId().equals(s.getClassId()) && place.section.getId().equals(s.getSectionId()))
                .toList();
        LocalDate today = LocalDate.now();
        for (int d = 1; d <= 5; d++) {
            LocalDate date = today.minusDays(d);
            for (int i = 0; i < roster.size(); i++) {
                Student student = roster.get(i);
                StudentAttendance row = new StudentAttendance();
                row.setTenantId(tenantId);
                row.setTimetableSlotId(slot.getId());
                row.setStudentId(student.getId());
                row.setTeacherUserId(slot.getTeacherUserId());
                row.setAttendanceDate(date);
                row.setStatus(i % 9 == 0 ? AttendanceStatus.ABSENT : (i % 11 == 0 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT));
                studentAttendanceRepository.save(row);
            }
        }
    }

    private void seedFees(UUID tenantId, List<Student> students) {
        if (feeChallanRepository.countByTenantId(tenantId) > 0 || students.isEmpty()) {
            return;
        }
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        int i = 0;
        for (Student student : students) {
            FeeChallan challan = new FeeChallan();
            challan.setTenantId(tenantId);
            challan.setStudentId(student.getId());
            challan.setChallanNumber(documentSequenceService.nextFormatted(tenantId, "CHL", "GVS-CHL-"));
            challan.setMonth(month);
            challan.setIssueDate(month);
            challan.setDueDate(month.plusDays(10));
            challan.setTuitionFee(new BigDecimal("8000"));
            challan.setStatus(i % 3 == 0 ? ChallanStatus.PAID : ChallanStatus.UNPAID);
            challan.recomputeTotal();
            feeChallanRepository.save(challan);
            i++;
        }
    }

    private void seedPublishedResults(UUID tenantId, List<Student> students, List<Subject> subjects) {
        if (students.isEmpty() || subjects.isEmpty()) {
            return;
        }
        ExamSession session = examSessionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(s -> "Mid-Term 2026".equalsIgnoreCase(s.getName()))
                .findFirst()
                .orElseGet(() -> {
                    ExamSession created = new ExamSession();
                    created.setTenantId(tenantId);
                    created.setName("Mid-Term 2026");
                    created.setAcademicSession("2026-2027");
                    created.setStartDate(LocalDate.now().minusDays(20));
                    created.setEndDate(LocalDate.now().minusDays(10));
                    created.setPublished(true);
                    created.setPublishedAt(Instant.now());
                    return examSessionRepository.save(created);
                });
        if (!session.isPublished()) {
            session.setPublished(true);
            session.setPublishedAt(Instant.now());
            examSessionRepository.save(session);
        }
        if (!examResultRepository.findByTenantIdAndExamSessionId(tenantId, session.getId()).isEmpty()) {
            return;
        }
        int studentIndex = 0;
        List<ExamResult> batch = new ArrayList<>();
        for (Student student : students) {
            int subjectIndex = 0;
            for (Subject subject : subjects) {
                int obtainedInt = 38 + ((studentIndex * 7 + subjectIndex * 13) % 58);
                BigDecimal total = new BigDecimal("100");
                BigDecimal obtained = new BigDecimal(obtainedInt);
                BigDecimal pct = GradeCalculator.percentage(obtained, total);
                ExamResult row = new ExamResult();
                row.setTenantId(tenantId);
                row.setExamSessionId(session.getId());
                row.setStudentId(student.getId());
                row.setSubjectId(subject.getId());
                row.setTotalMarks(total);
                row.setObtainedMarks(obtained);
                row.setPercentage(pct);
                row.setGrade(GradeCalculator.grade(pct));
                row.setPassStatus(GradeCalculator.passStatus(pct));
                batch.add(row);
                subjectIndex++;
            }
            studentIndex++;
        }
        examResultRepository.saveAll(batch);
    }

    private void seedSettings(UUID tenantId) {
        if (schoolSettingsRepository.findById(tenantId).isPresent()) {
            return;
        }
        SchoolSettings settings = new SchoolSettings();
        settings.setTenantId(tenantId);
        settings.setBankName("HBL");
        settings.setAccountTitle("Green Valley School");
        settings.setAccountNumber("1234-567890-01");
        settings.setJazzcash("03001234567");
        settings.setEasypaisa("03007654321");
        settings.setPaymentInstructions("Transfer tuition and upload the slip from Fees. Mention the challan number.");
        schoolSettingsRepository.save(settings);
    }
}
