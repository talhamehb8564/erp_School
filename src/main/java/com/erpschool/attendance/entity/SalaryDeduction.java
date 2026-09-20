package com.erpschool.attendance.entity;

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
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "salary_deductions")
public class SalaryDeduction extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "teacher_user_id", nullable = false)
    private UUID teacherUserId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "teacher_attendance_id")
    private UUID teacherAttendanceId;

    @Column(nullable = false)
    private boolean deduct = false;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(length = 300)
    private String reason;

    @Column(nullable = false)
    private LocalDate month;
}
