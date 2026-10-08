package com.erpschool.campus.entity;

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
@Table(name = "campuses")
public class Campus extends TenantAwareEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(length = 300)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 30)
    private String phone;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "primary_color", length = 16)
    private String primaryColor;

    @Column(name = "accent_color", length = 16)
    private String accentColor;
}
