package com.erpschool.admission;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.common.dto.PageResponse;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admissions")
@Tag(name = "Admissions")
public class AdmissionController {

    private final AdmissionService admissionService;

    public AdmissionController(AdmissionService admissionService) {
        this.admissionService = admissionService;
    }

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> config() {
        return ResponseEntity.ok(ApiResponse.ok(admissionService.adminConfig()));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> settings(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Admission settings saved", admissionService.saveSettings(body)));
    }

    @PostMapping("/fields")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addField(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Field added", admissionService.addField(body)));
    }

    @PutMapping("/fields/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateField(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Field updated", admissionService.updateField(id, body)));
    }

    @DeleteMapping("/fields/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteField(@PathVariable UUID id) {
        admissionService.deleteField(id);
        return ResponseEntity.ok(ApiResponse.ok("Field removed", null));
    }

    @PostMapping("/open-periods")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addPeriod(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Open period saved", admissionService.addPeriod(body)));
    }

    @PutMapping("/open-periods/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePeriod(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Open period updated", admissionService.updatePeriod(id, body)));
    }

    @DeleteMapping("/open-periods/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePeriod(@PathVariable UUID id) {
        admissionService.deletePeriod(id);
        return ResponseEntity.ok(ApiResponse.ok("Open period removed", null));
    }

    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<PageResponse<Map<String, Object>>>> applications(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(admissionService.applications(pageable)));
    }

    @GetMapping("/applications/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> application(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(admissionService.application(id)));
    }

    @PutMapping("/applications/{id}/status")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Application updated",
                admissionService.updateApplicationStatus(id, body.get("status"))));
    }

    @GetMapping("/slots")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> slots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID campusId) {
        return ResponseEntity.ok(ApiResponse.ok(admissionService.adminSlots(date, campusId)));
    }

    @PostMapping("/applications/{id}/appointment")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> book(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        Instant start = Instant.parse(body.get("slotStart"));
        UUID campusId = body.get("campusId") == null || body.get("campusId").isBlank()
                ? null : UUID.fromString(body.get("campusId"));
        return ResponseEntity.ok(ApiResponse.ok("Appointment booked",
                admissionService.bookForApplication(id, start, campusId)));
    }
}
