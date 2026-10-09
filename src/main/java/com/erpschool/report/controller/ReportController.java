package com.erpschool.report.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.report.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/dashboard/school")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> schoolDashboard() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.schoolDashboard()));
    }

    @GetMapping("/dashboard/platform")
    @PreAuthorize("hasRole('ERP_OWNER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> platformDashboard() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.platformDashboard()));
    }

    @GetMapping("/reports/attendance/students/{studentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> attendance(
            @PathVariable UUID studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.studentAttendance(studentId, from, to)));
    }

    @GetMapping("/reports/fees/collection")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','ACCOUNT_OFFICER','PRINCIPAL')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> fees(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.feeCollection(month)));
    }

    @GetMapping("/reports/exams/{sessionId}/classes/{classId}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> examClass(
            @PathVariable UUID sessionId, @PathVariable UUID classId) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.examClass(sessionId, classId)));
    }
}
