package com.erpschool.portal.controller;

import com.erpschool.academic.service.AcademicService;
import com.erpschool.common.dto.ApiResponse;
import com.erpschool.fee.entity.ChallanStatus;
import com.erpschool.fee.service.FeeService;
import com.erpschool.homework.service.HomeworkService;
import com.erpschool.notification.service.NotificationService;
import com.erpschool.report.service.ReportService;
import com.erpschool.salary.service.SalaryService;
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
    private final SalaryService salaryService;
    private final NotificationService notificationService;
    private final ReportService reportService;

    public PortalController(AcademicService academicService,
                            HomeworkService homeworkService,
                            StudentService studentService,
                            FeeService feeService,
                            SalaryService salaryService,
                            NotificationService notificationService,
                            ReportService reportService) {
        this.academicService = academicService;
        this.homeworkService = homeworkService;
        this.studentService = studentService;
        this.feeService = feeService;
        this.salaryService = salaryService;
        this.notificationService = notificationService;
        this.reportService = reportService;
    }

    @GetMapping("/teacher")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> teacher() {
        int day = LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY ? 7 : LocalDate.now().getDayOfWeek().getValue();
        Map<String, Object> m = new HashMap<>();
        m.put("todayLectures", academicService.teacherToday(day));
        m.put("assignments", academicService.teacherAssignments(null));
        m.put("homework", homeworkService.mine());
        m.put("unreadNotifications", notificationService.unreadCount());
        m.put("salaries", salaryService.mine());
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @GetMapping("/principal")
    @PreAuthorize("hasAnyRole('PRINCIPAL','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> principal() {
        Map<String, Object> m = new HashMap<>(reportService.schoolDashboard());
        m.put("unreadNotifications", notificationService.unreadCount());
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @GetMapping("/account")
    @PreAuthorize("hasRole('ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> account() {
        Map<String, Object> m = new HashMap<>(reportService.schoolDashboard());
        m.put("pendingProofs", feeService.pendingProofs());
        m.put("unpaidChallans", feeService.byStatus(ChallanStatus.UNPAID));
        m.put("unreadNotifications", notificationService.unreadCount());
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @GetMapping("/parent")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> parent() {
        var children = studentService.myChildren();
        Map<String, Object> m = new HashMap<>();
        m.put("children", children);
        m.put("homework", homeworkService.mine());
        m.put("fees", children.stream()
                .flatMap(child -> feeService.studentChallans(child.getId()).stream())
                .toList());
        m.put("unreadNotifications", notificationService.unreadCount());
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> student() {
        var profile = studentService.meAsStudent();
        Map<String, Object> m = new HashMap<>();
        m.put("profile", profile);
        m.put("homework", homeworkService.mine());
        m.put("fees", feeService.studentChallans(profile.getId()));
        m.put("unreadNotifications", notificationService.unreadCount());
        return ResponseEntity.ok(ApiResponse.ok(m));
    }
}
