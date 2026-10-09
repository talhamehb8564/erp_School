package com.erpschool.academic.entity;

import com.erpschool.common.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subjects")
public class Subject extends TenantAwareEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String code;
}
