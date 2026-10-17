package com.erpschool.student.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.common.dto.PageResponse;
import com.erpschool.student.dto.StudentDtos;
import com.erpschool.student.entity.ParentStudent;
import com.erpschool.student.entity.StudentStatus;
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
import java.util.Map;
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
    public ResponseEntity<ApiResponse<PageResponse<StudentDtos.ListRow>>> list(
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) String academicSession,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StudentStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(studentService.list(classId, sectionId, academicSession, q, status, pageable)));
    }

    @GetMapping("/students/roster")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> roster(
            @RequestParam UUID classId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) StudentStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(studentService.roster(classId, sectionId, status)));
    }

    @PostMapping("/students/inactivate-bulk")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> inactivateBulk(
            @Valid @RequestBody StudentDtos.InactivateBulkRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Students inactivated",
                studentService.inactivateBulk(
                        request.getClassId(), request.getSectionId(), request.getReason(), request.getNote())));
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

    @PostMapping("/students/promote")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<List<StudentDtos.Response>>> promote(@Valid @RequestBody PromoteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Students promoted", studentService.promote(
                request.getFromClassId(), request.getFromSectionId(),
                request.getToClassId(), request.getToSectionId(), request.getStudentIds())));
    }

    @PostMapping("/students/{id}/inactivate")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<StudentDtos.Response>> inactivate(
            @PathVariable UUID id, @Valid @RequestBody StudentDtos.StatusChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Student inactivated",
                studentService.inactivate(id, request.getReason(), request.getNote())));
    }

    @PostMapping("/students/{id}/reactivate")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<StudentDtos.Response>> reactivate(
            @PathVariable UUID id, @RequestBody(required = false) StudentDtos.ReactivateRequest request) {
        String note = request == null ? null : request.getNote();
        return ResponseEntity.ok(ApiResponse.ok("Student reactivated", studentService.reactivate(id, note)));
    }

    @GetMapping("/students/{id}/status-events")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<List<StudentDtos.StatusEventRow>>> statusEvents(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(studentService.statusHistory(id)));
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

    @Getter
    @Setter
    public static class PromoteRequest {
        @NotNull
        private UUID fromClassId;
        private UUID fromSectionId;
        @NotNull
        private UUID toClassId;
        @NotNull
        private UUID toSectionId;
        private List<UUID> studentIds;
    }

}
