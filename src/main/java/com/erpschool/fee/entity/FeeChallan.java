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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fee_challans")
public class FeeChallan extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "challan_number", nullable = false, length = 40)
    private String challanNumber;

    @Column(nullable = false)
    private LocalDate month;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "tuition_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal tuitionFee = BigDecimal.ZERO;

    @Column(name = "previous_outstanding", nullable = false, precision = 12, scale = 2)
    private BigDecimal previousOutstanding = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "additional_charges", nullable = false, precision = 12, scale = 2)
    private BigDecimal additionalCharges = BigDecimal.ZERO;

    @Column(name = "total_payable", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPayable = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ChallanStatus status = ChallanStatus.UNPAID;

    public void recomputeTotal() {
        totalPayable = tuitionFee
                .add(previousOutstanding)
                .add(additionalCharges)
                .subtract(discountAmount);
        if (totalPayable.signum() < 0) {
            totalPayable = BigDecimal.ZERO;
        }
    }

    public boolean isPastDue(LocalDate today) {
        return status == ChallanStatus.UNPAID
                && dueDate != null
                && today != null
                && dueDate.isBefore(today);
    }
}
