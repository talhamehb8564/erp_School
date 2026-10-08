package com.erpschool.exam.entity;

import com.erpschool.common.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exam_sessions")
public class ExamSession extends TenantAwareEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "academic_session", length = 50)
    private String academicSession;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "announce_at")
    private Instant announceAt;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.UUID)
    @Column(name = "campus_id")
    private java.util.UUID campusId;
}
