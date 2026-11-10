package com.erpschool.fee.entity;

import com.erpschool.common.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_fee_discounts")
public class StudentFeeDiscount extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(precision = 6, scale = 2)
    private BigDecimal percent;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 300)
    private String reason;

    @Column(nullable = false)
    private boolean active = true;
}
