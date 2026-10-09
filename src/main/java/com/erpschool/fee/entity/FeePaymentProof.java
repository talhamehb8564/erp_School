package com.erpschool.fee.entity;

import com.erpschool.common.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fee_payment_proofs")
public class FeePaymentProof extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "challan_id", nullable = false)
    private UUID challanId;

    @Column(name = "slip_url", nullable = false, length = 500)
    private String slipUrl;

    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ChallanStatus status = ChallanStatus.PAYMENT_UNDER_VERIFICATION;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
