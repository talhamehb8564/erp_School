package com.erpschool.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "school_settings")
public class SchoolSettings {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "payment_instructions", columnDefinition = "TEXT")
    private String paymentInstructions;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(name = "account_title", length = 150)
    private String accountTitle;

    @Column(name = "account_number", length = 80)
    private String accountNumber;

    @Column(length = 40)
    private String jazzcash;

    @Column(length = 40)
    private String easypaisa;

    @Column(name = "other_payment_methods", columnDefinition = "TEXT")
    private String otherPaymentMethods;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
