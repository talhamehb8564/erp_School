package com.erpschool.admission;

import com.erpschool.common.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "admission_fields")
public class AdmissionField extends TenantAwareEntity {

    @Column(name = "field_key", nullable = false, length = 60)
    private String fieldKey;

    @Column(nullable = false, length = 120)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 20)
    private AdmissionFieldType fieldType = AdmissionFieldType.TEXT;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private boolean locked;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(columnDefinition = "TEXT")
    private String options;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
