package com.erpschool.remark.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.common.dto.PageResponse;
import com.erpschool.remark.service.RemarkService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/remarks")
@Tag(name = "Remarks")
public class RemarkController {

    private final RemarkService remarkService;

    public RemarkController(RemarkService remarkService) {
        this.remarkService = remarkService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Remark saved", remarkService.create(body)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Remark updated", remarkService.update(id, body)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        remarkService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Remark deleted", null));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<Map<String, Object>>>> list(
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 40) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                remarkService.list(studentId, classId, sectionId, from, to, pageable)));
    }
}
