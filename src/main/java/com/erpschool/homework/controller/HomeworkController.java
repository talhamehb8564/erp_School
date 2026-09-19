package com.erpschool.homework.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.homework.entity.Homework;
import com.erpschool.homework.entity.HomeworkSubmission;
import com.erpschool.homework.service.HomeworkService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/homework")
@Tag(name = "Homework")
public class HomeworkController {

    private final HomeworkService homeworkService;

    public HomeworkController(HomeworkService homeworkService) {
        this.homeworkService = homeworkService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody CreateRequest request) {
        Homework h = new Homework();
        h.setTeacherUserId(request.getTeacherUserId());
        h.setClassId(request.getClassId());
        h.setSectionId(request.getSectionId());
        h.setSubjectId(request.getSubjectId());
        h.setTitle(request.getTitle());
        h.setDescription(request.getDescription());
        h.setDueDate(request.getDueDate());
        List<HomeworkService.AttachmentIn> atts = request.getAttachments() == null ? List.of()
                : request.getAttachments().stream()
                .map(a -> new HomeworkService.AttachmentIn(a.getFileName(), a.getFileUrl(), a.getContentType()))
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Homework assigned", homeworkService.create(h, atts)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID sectionId) {
        if (classId != null && sectionId != null) {
            return ResponseEntity.ok(ApiResponse.ok(homeworkService.forClass(classId, sectionId)));
        }
        return ResponseEntity.ok(ApiResponse.ok(homeworkService.mine()));
    }

    @PostMapping("/{id}/submissions")
    @PreAuthorize("hasAnyRole('STUDENT','PARENT','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<HomeworkSubmission>> submit(
            @PathVariable UUID id, @RequestBody SubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Homework submitted", homeworkService.submit(
                        id, request.getStudentId(), request.getFileUrl(), request.getNotes())));
    }

    @GetMapping("/{id}/submissions")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<List<HomeworkSubmission>>> submissions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(homeworkService.submissions(id)));
    }

    @PostMapping("/submissions/{submissionId}/review")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER')")
    public ResponseEntity<ApiResponse<HomeworkSubmission>> review(
            @PathVariable UUID submissionId, @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Submission reviewed",
                homeworkService.review(submissionId, request == null ? null : request.getRemark())));
    }

    @Getter
    @Setter
    public static class CreateRequest {
        private UUID teacherUserId;
        @NotNull
        private UUID classId;
        @NotNull
        private UUID sectionId;
        @NotNull
        private UUID subjectId;
        @NotBlank
        private String title;
        private String description;
        @NotNull
        private LocalDate dueDate;
        private List<Att> attachments;
    }

    @Getter
    @Setter
    public static class Att {
        private String fileName;
        private String fileUrl;
        private String contentType;
    }

    @Getter
    @Setter
    public static class SubmitRequest {
        private UUID studentId;
        private String fileUrl;
        private String notes;
    }

    @Getter
    @Setter
    public static class ReviewRequest {
        private String remark;
    }
}
