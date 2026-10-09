package com.erpschool.academic.entity;

import com.erpschool.common.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "school_classes")
public class SchoolClass extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "campus_id")
    private UUID campusId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 40)
    private String grade;

    @Column(name = "academic_session", length = 50)
    private String academicSession;
}
