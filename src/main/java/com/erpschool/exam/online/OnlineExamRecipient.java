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

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "online_exam_recipients")
public class OnlineExamRecipient extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false)
    private boolean excluded;

    @Column(name = "exclude_reason", length = 40)
    private String excludeReason;

    @Column(name = "exclude_note", length = 300)
    private String excludeNote;
}
