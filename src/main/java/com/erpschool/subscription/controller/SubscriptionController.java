package com.erpschool.subscription.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.common.dto.PageResponse;
import com.erpschool.subscription.dto.SubscriptionDtos;
import com.erpschool.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "Subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ERP_OWNER')")
    public ResponseEntity<ApiResponse<SubscriptionDtos.Response>> create(
            @Valid @RequestBody SubscriptionDtos.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Subscription created", subscriptionService.create(request)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ERP_OWNER')")
    public ResponseEntity<ApiResponse<PageResponse<SubscriptionDtos.Response>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.listAll(pageable)));
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionDtos.Response>> current(
            @RequestParam(required = false) UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.current(tenantId)));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionDtos.Response>> submit(
            @PathVariable UUID id,
            @Valid @RequestBody SubscriptionDtos.SubmitPaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Payment proof submitted",
                subscriptionService.submitPayment(id, request)));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('ERP_OWNER')")
    public ResponseEntity<ApiResponse<SubscriptionDtos.Response>> review(
            @PathVariable UUID id,
            @Valid @RequestBody SubscriptionDtos.ReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Subscription reviewed",
                subscriptionService.review(id, request)));
    }
}
