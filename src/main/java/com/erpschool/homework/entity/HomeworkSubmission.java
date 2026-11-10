package com.erpschool.homework.entity;

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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "homework_submissions")
public class HomeworkSubmission extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "homework_id", nullable = false)
    private UUID homeworkId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HomeworkSubmissionStatus status = HomeworkSubmissionStatus.SUBMITTED;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "teacher_remark", length = 300)
    private String teacherRemark;

    public static HomeworkSubmissionStatus statusForDueDate(LocalDate dueDate, LocalDate submittedOn) {
        if (dueDate != null && submittedOn != null && submittedOn.isAfter(dueDate)) {
            return HomeworkSubmissionStatus.LATE;
        }
        return HomeworkSubmissionStatus.SUBMITTED;
    }
}
