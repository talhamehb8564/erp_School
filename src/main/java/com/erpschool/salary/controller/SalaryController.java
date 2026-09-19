package com.erpschool.salary.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.salary.entity.StaffProfile;
import com.erpschool.salary.entity.StaffSalary;
import com.erpschool.salary.service.SalaryService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/salaries")
@Tag(name = "Salaries")
public class SalaryController {

    private final SalaryService salaryService;

    public SalaryController(SalaryService salaryService) {
        this.salaryService = salaryService;
    }

    @PostMapping("/profiles")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<StaffProfile>> profile(@RequestBody ProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Staff salary profile saved",
                salaryService.upsertProfile(request.getUserId(), request.getBaseSalary())));
    }

    @GetMapping("/profiles")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','ACCOUNT_OFFICER','PRINCIPAL')")
    public ResponseEntity<ApiResponse<List<StaffProfile>>> profiles() {
        return ResponseEntity.ok(ApiResponse.ok(salaryService.profiles()));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<List<StaffSalary>>> generate(@RequestBody GenerateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Monthly salaries generated",
                salaryService.generateMonth(request.getMonth())));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<StaffSalary>> pay(@PathVariable UUID id, @RequestBody(required = false) PayRequest request) {
        LocalDate date = request == null ? null : request.getPaymentDate();
        return ResponseEntity.ok(ApiResponse.ok("Salary marked paid", salaryService.markPaid(id, date)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','ACCOUNT_OFFICER','PRINCIPAL')")
    public ResponseEntity<ApiResponse<List<StaffSalary>>> month(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return ResponseEntity.ok(ApiResponse.ok(salaryService.month(month)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN','PRINCIPAL','TEACHER','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<List<StaffSalary>>> mine() {
        return ResponseEntity.ok(ApiResponse.ok(salaryService.mine()));
    }

    @Getter
    @Setter
    public static class ProfileRequest {
        @NotNull
        private UUID userId;
        @NotNull
        private BigDecimal baseSalary;
    }

    @Getter
    @Setter
    public static class GenerateRequest {
        @NotNull
        private LocalDate month;
    }

    @Getter
    @Setter
    public static class PayRequest {
        private LocalDate paymentDate;
    }
}
