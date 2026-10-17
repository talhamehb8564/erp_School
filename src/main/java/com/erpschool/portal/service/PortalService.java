package com.erpschool.portal.service;

import com.erpschool.academic.service.AcademicService;
import com.erpschool.fee.service.FeeService;
import com.erpschool.homework.service.HomeworkService;
import com.erpschool.report.service.ReportService;
import com.erpschool.student.dto.StudentDtos;
import com.erpschool.student.service.StudentService;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PortalService {

    private static final int DASH_HOMEWORK = 8;
    private static final int DASH_FEES = 20;
    private static final int DASH_PROOFS = 12;

    private final AcademicService academicService;
    private final HomeworkService homeworkService;
    private final StudentService studentService;
    private final FeeService feeService;
    private final ReportService reportService;

    public PortalService(AcademicService academicService,
                         HomeworkService homeworkService,
                         StudentService studentService,
                         FeeService feeService,
                         ReportService reportService) {
        this.academicService = academicService;
        this.homeworkService = homeworkService;
        this.studentService = studentService;
        this.feeService = feeService;
        this.reportService = reportService;
    }

    public Map<String, Object> teacher() {
        int day = LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY ? 7 : LocalDate.now().getDayOfWeek().getValue();
        Map<String, Object> m = new HashMap<>();
        m.put("todayLectures", academicService.teacherToday(day));
        m.put("homework", homeworkService.mine(DASH_HOMEWORK));
        m.put("progress", reportService.teacherProgress());
        return m;
    }

    public Map<String, Object> principal() {
        return new HashMap<>(reportService.schoolDashboard());
    }

    public Map<String, Object> account() {
        Map<String, Object> m = new HashMap<>(reportService.schoolDashboard());
        m.put("pendingProofs", feeService.pendingProofs(DASH_PROOFS));
        return m;
    }

    public Map<String, Object> parent() {
        List<StudentDtos.ListRow> children = studentService.myChildrenRows();
        List<UUID> childIds = children.stream().map(StudentDtos.ListRow::getId).toList();
        Map<String, Object> m = new HashMap<>();
        m.put("children", children);
        m.put("fees", feeService.challanSummariesForStudents(childIds, DASH_FEES));
        m.put("progressByChild", reportService.studentProgressBatch(childIds));
        return m;
    }

    public Map<String, Object> student() {
        StudentDtos.ListRow profile = studentService.meAsStudentRow();
        Map<String, Object> m = new HashMap<>();
        m.put("profile", profile);
        m.put("homework", homeworkService.mine(DASH_HOMEWORK));
        m.put("fees", feeService.challanSummariesForStudents(List.of(profile.getId()), DASH_FEES));
        m.put("progress", reportService.studentProgress(profile.getId()));
        return m;
    }
}
