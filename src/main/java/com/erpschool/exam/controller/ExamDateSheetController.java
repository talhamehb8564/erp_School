package com.erpschool.exam.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.exam.service.ExamDateSheetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exams/date-sheets")
@Tag(name = "Date sheets")
public class ExamDateSheetController {

    private final ExamDateSheetService dateSheetService;

    public ExamDateSheetController(ExamDateSheetService dateSheetService) {
        this.dateSheetService = dateSheetService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false) UUID sessionId,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) String academicSession) {
        return ResponseEntity.ok(ApiResponse.ok(
                dateSheetService.list(sessionId, classId, sectionId, published, academicSession)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(dateSheetService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @RequestBody ExamDateSheetService.SaveRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Date sheet saved", dateSheetService.save(null, body)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable UUID id, @RequestBody ExamDateSheetService.SaveRequest body) {
        return ResponseEntity.ok(ApiResponse.ok("Date sheet saved", dateSheetService.save(id, body)));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Date sheet published", dateSheetService.publish(id, true)));
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> unpublish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Date sheet unpublished", dateSheetService.publish(id, false)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        dateSheetService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Date sheet deleted", null));
    }
}
