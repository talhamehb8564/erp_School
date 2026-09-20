package com.erpschool.student.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.common.dto.PageResponse;
import com.erpschool.student.dto.StudentDtos;
import com.erpschool.student.entity.ParentStudent;
import com.erpschool.student.service.StudentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping("/students")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<StudentDtos.EnrollResponse>> enroll(
            @Valid @RequestBody StudentDtos.EnrollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Student enrolled", studentService.enroll(request)));
    }

    @GetMapping("/students")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<PageResponse<StudentDtos.Response>>> list(
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID sectionId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(studentService.list(classId, sectionId, pageable)));
    }

    @GetMapping("/students/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<StudentDtos.Response>> me() {
        return ResponseEntity.ok(ApiResponse.ok(studentService.meAsStudent()));
    }

    @GetMapping("/students/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<StudentDtos.Response>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(studentService.get(id)));
    }

    @PutMapping("/students/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<StudentDtos.Response>> update(
            @PathVariable UUID id, @Valid @RequestBody StudentDtos.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Student updated", studentService.update(id, request)));
    }

    @PostMapping("/students/{id}/parents")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<ParentStudent>> linkParent(
            @PathVariable UUID id, @Valid @RequestBody LinkParentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Parent linked",
                        studentService.linkParent(id, request.getParentUserId(), request.getRelationship())));
    }

    @GetMapping("/parents/me/children")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<List<StudentDtos.Response>>> children() {
        return ResponseEntity.ok(ApiResponse.ok(studentService.myChildren()));
    }

    @Getter
    @Setter
    public static class LinkParentRequest {
        @NotNull
        private UUID parentUserId;
        private String relationship;
    }
}
