package com.erpschool.homework;

import com.erpschool.homework.entity.HomeworkSubmission;
import com.erpschool.homework.entity.HomeworkSubmissionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class HomeworkSubmissionTest {

    @Test
    void onTimeSubmissionIsSubmitted() {
        assertThat(HomeworkSubmission.statusForDueDate(
                LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20)))
                .isEqualTo(HomeworkSubmissionStatus.SUBMITTED);
    }

    @Test
    void afterDueDateIsLate() {
        assertThat(HomeworkSubmission.statusForDueDate(
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11)))
                .isEqualTo(HomeworkSubmissionStatus.LATE);
    }
}
