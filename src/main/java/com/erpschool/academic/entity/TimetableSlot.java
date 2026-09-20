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

import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "timetable_slots")
public class TimetableSlot extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "teacher_user_id", nullable = false)
    private UUID teacherUserId;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
