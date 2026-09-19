package com.erpschool.attendance.controller;

import com.erpschool.attendance.entity.AttendanceStatus;
import com.erpschool.attendance.entity.StudentAttendance;
import com.erpschool.attendance.entity.TeacherAttendance;
import com.erpschool.attendance.service.AttendanceService;
import com.erpschool.common.dto.ApiResponse;
import com.erpschool.student.entity.Student;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance")
@Tag(name = "Attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/lectures/{slotId}/roster")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<List<Student>>> roster(@PathVariable UUID slotId) {
        return ResponseEntity.ok(ApiResponse.ok(attendanceService.lectureRoster(slotId)));
    }

    @PostMapping("/lectures/{slotId}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<List<StudentAttendance>>> markLecture(
            @PathVariable UUID slotId, @Valid @RequestBody LectureMarkRequest request) {
        List<AttendanceService.MarkItem> items = request.getMarks().stream()
                .map(m -> new AttendanceService.MarkItem(m.getStudentId(), m.getStatus(), m.getRemarks()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Lecture attendance saved",
                attendanceService.markLecture(slotId, request.getDate(), items)));
    }

    @GetMapping("/lectures/{slotId}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<List<StudentAttendance>>> lectureSheet(
            @PathVariable UUID slotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(attendanceService.lectureSheet(slotId, date)));
    }

    @GetMapping("/students/{studentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<StudentAttendance>>> studentHistory(
            @PathVariable UUID studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(attendanceService.studentHistory(studentId, from, to)));
    }

    @PostMapping("/teachers")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<TeacherAttendance>> markTeacher(@Valid @RequestBody TeacherMarkRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Teacher attendance saved",
                attendanceService.markTeacher(request.getTeacherUserId(), request.getDate(), request.getStatus(),
                        request.getRemarks(), Boolean.TRUE.equals(request.getDeductSalary()),
                        request.getDeductionAmount())));
    }

    @GetMapping("/teachers")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<List<TeacherAttendance>>> teacherHistory(
            @RequestParam(required = false) UUID teacherUserId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(attendanceService.teacherHistory(teacherUserId, from, to)));
    }

    @Getter
    @Setter
    public static class LectureMarkRequest {
        @NotNull
        private LocalDate date;
        @NotEmpty
        private List<Mark> marks;
    }

    @Getter
    @Setter
    public static class Mark {
        @NotNull
        private UUID studentId;
        @NotNull
        private AttendanceStatus status;
        private String remarks;
    }

    @Getter
    @Setter
    public static class TeacherMarkRequest {
        @NotNull
        private UUID teacherUserId;
        @NotNull
        private LocalDate date;
        @NotNull
        private AttendanceStatus status;
        private String remarks;
        private Boolean deductSalary;
        private BigDecimal deductionAmount;
    }
}
