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
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "online_exam_answers")
public class OnlineExamAnswer extends TenantAwareEntity {

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "option_choice", length = 1)
    private String optionChoice;

    @Column(name = "text_answer", columnDefinition = "TEXT")
    private String textAnswer;

    @Column(name = "obtained_marks", precision = 8, scale = 2)
    private BigDecimal obtainedMarks;

    @Column(name = "auto_graded", nullable = false)
    private boolean autoGraded;
}
