package com.erpschool.subscription.dto;

import com.erpschool.subscription.entity.Subscription;
import com.erpschool.subscription.entity.SubscriptionPayment;
import com.erpschool.subscription.entity.SubscriptionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    @Getter
    @Setter
    public static class CreateRequest {
        @NotNull
        private UUID tenantId;
        private BigDecimal amount;
        private String currency;
        private String notes;
    }

    @Getter
    @Setter
    public static class SubmitPaymentRequest {
        @NotBlank
        private String slipUrl;
        private String transactionRef;
    }

    @Getter
    @Setter
    public static class ReviewRequest {
        @NotNull
        private Boolean approve;
        private String rejectionReason;
        private LocalDate periodStart;
        private LocalDate periodEnd;
    }

    @Getter
    @Builder
    public static class PaymentResponse {
        private UUID id;
        private String slipUrl;
        private String transactionRef;
        private SubscriptionStatus status;
        private Instant submittedAt;
        private Instant reviewedAt;
        private String rejectionReason;
    }

    @Getter
    @Builder
    public static class Response {
        private UUID id;
        private UUID tenantId;
        private SubscriptionStatus status;
        private BigDecimal amount;
        private String currency;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String notes;
        private Instant createdAt;
        private List<PaymentResponse> payments;
    }

    public static Response from(Subscription s, List<SubscriptionPayment> payments) {
        return Response.builder()
                .id(s.getId())
                .tenantId(s.getTenantId())
                .status(s.getStatus())
                .amount(s.getAmount())
                .currency(s.getCurrency())
                .periodStart(s.getPeriodStart())
                .periodEnd(s.getPeriodEnd())
                .notes(s.getNotes())
                .createdAt(s.getCreatedAt())
                .payments(payments.stream().map(p -> PaymentResponse.builder()
                        .id(p.getId())
                        .slipUrl(p.getSlipUrl())
                        .transactionRef(p.getTransactionRef())
                        .status(p.getStatus())
                        .submittedAt(p.getSubmittedAt())
                        .reviewedAt(p.getReviewedAt())
                        .rejectionReason(p.getRejectionReason())
                        .build()).toList())
                .build();
    }
}
