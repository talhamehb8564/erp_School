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
@Table(name = "fee_structures")
public class FeeStructure extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "class_id")
    private UUID classId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Column(name = "tuition_amount", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal tuitionAmount = BigDecimal.ZERO;
}
