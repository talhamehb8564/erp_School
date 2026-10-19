package com.erpschool.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "timetable_settings")
public class TimetableSettings {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime = LocalTime.of(8, 0);

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime = LocalTime.of(13, 30);

    @Column(name = "lecture_minutes", nullable = false)
    private int lectureMinutes = 45;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes = 15;

    @Column(name = "lectures_per_day", nullable = false)
    private int lecturesPerDay = 7;

    @Column(name = "working_days", nullable = false, length = 40)
    private String workingDays = "1,2,3,4,5";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "created_by")
    private UUID createdBy;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "updated_by")
    private UUID updatedBy;
}
