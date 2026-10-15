package com.erpschool.exam.online;

import com.erpschool.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/online-exams")
@Tag(name = "Online exams")
public class OnlineExamController {

    private final OnlineExamService onlineExamService;

    public OnlineExamController(OnlineExamService onlineExamService) {
        this.onlineExamService = onlineExamService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Exam created", onlineExamService.create(body)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Exam updated", onlineExamService.update(id, body)));
    }

    @PostMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> questions(
            @PathVariable UUID id, @RequestBody List<Map<String, Object>> body) {
        return ResponseEntity.ok(ApiResponse.ok("Questions saved", onlineExamService.replaceQuestions(id, body)));
    }

    @PostMapping("/{id}/schedule")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> schedule(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Exam scheduled", onlineExamService.schedule(id)));
    }

    @PostMapping("/{id}/publish-results")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Results published", onlineExamService.publishResults(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(onlineExamService.staffList()));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> mine() {
        return ResponseEntity.ok(ApiResponse.ok(onlineExamService.studentList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(onlineExamService.staffGet(id)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> start(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(onlineExamService.start(id)));
    }

    @PutMapping("/{id}/answers")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> answers(
            @PathVariable UUID id, @RequestBody List<Map<String, Object>> body) {
        return ResponseEntity.ok(ApiResponse.ok(onlineExamService.saveAnswers(id, body)));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Submitted", onlineExamService.submit(id, false)));
    }

    @PostMapping("/{id}/auto-submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> autoSubmit(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Auto-submitted", onlineExamService.submit(id, true)));
    }

    @GetMapping("/{id}/result")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> result(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(onlineExamService.studentResult(id)));
    }

    @GetMapping("/{id}/attempts")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> attempts(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(onlineExamService.attempts(id)));
    }

    @GetMapping("/{id}/attempts/{attemptId}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> attempt(
            @PathVariable UUID id, @PathVariable UUID attemptId) {
        return ResponseEntity.ok(ApiResponse.ok(onlineExamService.attemptDetail(id, attemptId)));
    }

    @PostMapping("/{id}/attempts/{attemptId}/grade")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> grade(
            @PathVariable UUID id, @PathVariable UUID attemptId, @RequestBody List<Map<String, Object>> body) {
        return ResponseEntity.ok(ApiResponse.ok("Graded", onlineExamService.grade(id, attemptId, body)));
    }
}
