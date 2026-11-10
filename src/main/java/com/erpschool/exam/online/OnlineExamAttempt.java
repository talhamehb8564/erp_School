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
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "online_exam_attempts")
public class OnlineExamAttempt extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "auto_submitted", nullable = false)
    private boolean autoSubmitted;

    @Column(nullable = false, length = 30)
    private String status = "IN_PROGRESS";

    @Column(name = "mcq_obtained", nullable = false, precision = 8, scale = 2)
    private BigDecimal mcqObtained = BigDecimal.ZERO;

    @Column(name = "short_obtained", nullable = false, precision = 8, scale = 2)
    private BigDecimal shortObtained = BigDecimal.ZERO;

    @Column(name = "long_obtained", nullable = false, precision = 8, scale = 2)
    private BigDecimal longObtained = BigDecimal.ZERO;

    @Column(name = "obtained_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal obtainedMarks = BigDecimal.ZERO;

    @Column(name = "total_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalMarks = BigDecimal.ZERO;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal percentage = BigDecimal.ZERO;

    @Column(length = 5)
    private String grade;

    @Column(name = "pass_status", length = 10)
    private String passStatus;
}
