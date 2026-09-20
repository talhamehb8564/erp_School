package com.erpschool.student.entity;

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

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "students")
public class Student extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "user_id")
    private UUID userId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "campus_id")
    private UUID campusId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "class_id")
    private UUID classId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "admission_number", nullable = false, length = 40)
    private String admissionNumber;

    @Column(name = "registration_number", length = 40)
    private String registrationNumber;

    @Column(name = "roll_number", length = 20)
    private String rollNumber;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(length = 20)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudentStatus status = StudentStatus.ACTIVE;

    @Column(length = 300)
    private String address;

    @Column(name = "guardian_name", length = 150)
    private String guardianName;

    @Column(name = "guardian_phone", length = 30)
    private String guardianPhone;
}
