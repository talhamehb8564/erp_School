package com.erpschool.fee.entity;

import com.erpschool.common.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fee_charge_types")
public class FeeChargeType extends TenantAwareEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "default_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;
}
