package com.erpschool.exam.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.exam.entity.ExamResult;
import com.erpschool.exam.entity.ExamSession;
import com.erpschool.exam.service.ExamService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exams")
@Tag(name = "Exams")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<ExamSession>> create(@RequestBody ExamSession body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Exam session created", examService.createSession(body)));
    }

    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ExamSession>>> sessions() {
        return ResponseEntity.ok(ApiResponse.ok(examService.sessions()));
    }

    @PostMapping("/sessions/{id}/results")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<ExamResult>> upsert(
            @PathVariable UUID id, @Valid @RequestBody ResultRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Result saved", examService.upsertResult(
                id, request.getStudentId(), request.getSubjectId(),
                request.getTotalMarks(), request.getObtainedMarks(), request.getRemarks())));
    }

    @PostMapping("/sessions/{id}/publish")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<ExamSession>> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Results published", examService.publish(id)));
    }

    @GetMapping("/sessions/{id}/results")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<List<ExamResult>>> all(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(examService.sessionResults(id)));
    }

    @GetMapping("/sessions/{id}/students/{studentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> student(
            @PathVariable UUID id, @PathVariable UUID studentId) {
        return ResponseEntity.ok(ApiResponse.ok(examService.studentResult(id, studentId)));
    }

    @GetMapping("/sessions/{id}/results/by-roll")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> byRoll(
            @PathVariable UUID id, @RequestParam String rollNumber) {
        return ResponseEntity.ok(ApiResponse.ok(examService.studentResultByRoll(id, rollNumber)));
    }

    @Getter
    @Setter
    public static class ResultRequest {
        @NotNull
        private UUID studentId;
        @NotNull
        private UUID subjectId;
        @NotNull
        private BigDecimal totalMarks;
        @NotNull
        private BigDecimal obtainedMarks;
        private String remarks;
    }
}
