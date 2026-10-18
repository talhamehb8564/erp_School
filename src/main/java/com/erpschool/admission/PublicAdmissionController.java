package com.erpschool.admission;

import com.erpschool.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/public/admission")
@Tag(name = "Public admissions")
public class PublicAdmissionController {

    private final AdmissionService admissionService;

    public PublicAdmissionController(AdmissionService admissionService) {
        this.admissionService = admissionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> form(@RequestParam(required = false) String code) {
        return ResponseEntity.ok(ApiResponse.ok(admissionService.publicForm(code)));
    }

    @GetMapping("/slots")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> slots(
            @RequestParam(required = false) String code,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID campusId) {
        return ResponseEntity.ok(ApiResponse.ok(admissionService.publicSlots(code, date, campusId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(
            @RequestParam(required = false) String code,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Application submitted", admissionService.submitPublic(code, body)));
    }
}
