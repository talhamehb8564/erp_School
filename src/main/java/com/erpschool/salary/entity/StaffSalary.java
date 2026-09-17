package com.erpschool.salary.entity;

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
@Table(name = "staff_salaries")
public class StaffSalary extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Column(nullable = false)
    private LocalDate month;

    @Column(name = "base_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseSalary = BigDecimal.ZERO;

    @Column(name = "attendance_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal attendanceDeductions = BigDecimal.ZERO;

    @Column(name = "other_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal bonuses = BigDecimal.ZERO;

    @Column(name = "final_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalSalary = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalaryStatus status = SalaryStatus.UNPAID;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(length = 300)
    private String notes;

    public void recompute() {
        finalSalary = baseSalary.subtract(attendanceDeductions).subtract(otherDeductions).add(bonuses);
        if (finalSalary.signum() < 0) {
            finalSalary = BigDecimal.ZERO;
        }
    }
}
