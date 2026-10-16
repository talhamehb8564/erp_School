package com.erpschool.portal.controller;

import com.erpschool.academic.service.AcademicService;
import com.erpschool.common.dto.ApiResponse;
import com.erpschool.fee.service.FeeService;
import com.erpschool.homework.service.HomeworkService;
import com.erpschool.report.service.ReportService;
import com.erpschool.student.service.StudentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/portals")
@Tag(name = "Portals")
public class PortalController {

    private final AcademicService academicService;
    private final HomeworkService homeworkService;
    private final StudentService studentService;
    private final FeeService feeService;
    private final ReportService reportService;

    public PortalController(AcademicService academicService,
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

    @GetMapping("/teacher")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> teacher() {
        int day = LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY ? 7 : LocalDate.now().getDayOfWeek().getValue();
        Map<String, Object> m = new HashMap<>();
        m.put("todayLectures", academicService.teacherToday(day));
        m.put("homework", homeworkService.mine(8));
        m.put("progress", reportService.teacherProgress());
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @GetMapping("/principal")
    @PreAuthorize("hasAnyRole('PRINCIPAL','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> principal() {
        return ResponseEntity.ok(ApiResponse.ok(new HashMap<>(reportService.schoolDashboard())));
    }

    @GetMapping("/account")
    @PreAuthorize("hasRole('ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> account() {
        Map<String, Object> m = new HashMap<>(reportService.schoolDashboard());
        m.put("pendingProofs", feeService.pendingProofs());
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @GetMapping("/parent")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> parent() {
        var children = studentService.myChildren();
        var childIds = children.stream().map(c -> c.getId()).toList();
        Map<String, Object> m = new HashMap<>();
        m.put("children", children);
        m.put("fees", feeService.challansForStudents(childIds));
        m.put("progressByChild", reportService.studentProgressBatch(childIds));
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> student() {
        var profile = studentService.meAsStudent();
        Map<String, Object> m = new HashMap<>();
        m.put("profile", profile);
        m.put("homework", homeworkService.mine(8));
        m.put("fees", feeService.studentChallans(profile.getId()));
        m.put("progress", reportService.studentProgress(profile.getId()));
        return ResponseEntity.ok(ApiResponse.ok(m));
    }
}
