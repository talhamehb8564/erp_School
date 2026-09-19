package com.erpschool.fee.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.fee.entity.ChallanStatus;
import com.erpschool.fee.entity.FeeChallan;
import com.erpschool.fee.entity.FeePaymentProof;
import com.erpschool.fee.entity.FeeStructure;
import com.erpschool.fee.service.FeeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fees")
@Tag(name = "Fees")
public class FeeController {

    private final FeeService feeService;

    public FeeController(FeeService feeService) {
        this.feeService = feeService;
    }

    @PostMapping("/structures")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<FeeStructure>> structure(@RequestBody FeeStructure body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Fee structure saved", feeService.saveStructure(body)));
    }

    @GetMapping("/structures")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','ACCOUNT_OFFICER','PRINCIPAL')")
    public ResponseEntity<ApiResponse<List<FeeStructure>>> structures() {
        return ResponseEntity.ok(ApiResponse.ok(feeService.structures()));
    }

    @PostMapping("/challans/generate")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> generate(@RequestBody GenerateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Challans generated", feeService.generateMonthly(
                        request.getClassId(), request.getMonth(), request.getDueDate(),
                        request.getAdditionalCharges(), request.getDiscountAmount())));
    }

    @GetMapping("/students/{studentId}/challans")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> student(@PathVariable UUID studentId) {
        return ResponseEntity.ok(ApiResponse.ok(feeService.studentChallans(studentId)));
    }

    @PostMapping("/challans/{id}/proofs")
    @PreAuthorize("hasAnyRole('PARENT','STUDENT','SCHOOL_ADMIN','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> proof(
            @PathVariable UUID id, @RequestBody ProofRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Payment proof submitted",
                feeService.submitProof(id, request.getSlipUrl(), request.getTransactionRef())));
    }

    @PostMapping("/proofs/{id}/review")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> review(
            @PathVariable UUID id, @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Payment reviewed",
                feeService.reviewProof(id, Boolean.TRUE.equals(request.getApprove()), request.getRemarks())));
    }

    @GetMapping("/proofs/pending")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<List<FeePaymentProof>>> pending() {
        return ResponseEntity.ok(ApiResponse.ok(feeService.pendingProofs()));
    }

    @GetMapping("/challans")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','ACCOUNT_OFFICER','PRINCIPAL')")
    public ResponseEntity<ApiResponse<List<FeeChallan>>> byStatus(@RequestParam ChallanStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(feeService.byStatus(status)));
    }

    @PostMapping("/challans/mark-overdue")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<List<FeeChallan>>> markOverdue() {
        return ResponseEntity.ok(ApiResponse.ok("Overdue challans updated", feeService.markOverdue()));
    }

    @Getter
    @Setter
    public static class GenerateRequest {
        @NotNull
        private UUID classId;
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate month;
        private LocalDate dueDate;
        private BigDecimal additionalCharges;
        private BigDecimal discountAmount;
    }

    @Getter
    @Setter
    public static class ProofRequest {
        @NotBlank
        private String slipUrl;
        private String transactionRef;
    }

    @Getter
    @Setter
    public static class ReviewRequest {
        @NotNull
        private Boolean approve;
        private String remarks;
    }
}
