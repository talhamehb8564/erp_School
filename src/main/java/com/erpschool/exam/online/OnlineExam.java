package com.erpschool.exam.online;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "online_exams")
public class OnlineExam extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "campus_id")
    private UUID campusId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.erpschool.common.util.FlexibleInstantDeserializer.class)
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "total_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalMarks = BigDecimal.ZERO;

    @Column(name = "passing_marks", precision = 8, scale = 2)
    private BigDecimal passingMarks;

    @Column(name = "mcq_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal mcqMarks = BigDecimal.ONE;

    @Column(name = "short_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal shortMarks = BigDecimal.valueOf(2);

    @Column(name = "long_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal longMarks = BigDecimal.valueOf(5);

    @Column(name = "short_required")
    private Integer shortRequired;

    @Column(name = "long_required")
    private Integer longRequired;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "published_at")
    private Instant publishedAt;
}
