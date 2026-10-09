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
@Table(name = "teacher_assignments")
public class TeacherAssignment extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "teacher_user_id", nullable = false)
    private UUID teacherUserId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;
}
