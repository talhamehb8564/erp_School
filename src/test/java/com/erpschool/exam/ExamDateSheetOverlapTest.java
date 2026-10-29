package com.erpschool.exam;

import com.erpschool.exam.service.ExamDateSheetService;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ExamDateSheetOverlapTest {

    @Test
    void overlappingWindowsAreRejected() {
        assertThat(ExamDateSheetService.timesOverlap(
                LocalTime.of(9, 0), LocalTime.of(11, 0),
                LocalTime.of(10, 0), LocalTime.of(12, 0))).isTrue();
    }

    @Test
    void adjacentWindowsAreAllowed() {
        assertThat(ExamDateSheetService.timesOverlap(
                LocalTime.of(9, 0), LocalTime.of(11, 0),
                LocalTime.of(11, 0), LocalTime.of(12, 0))).isFalse();
    }

    @Test
    void missingTimesDoNotOverlap() {
        assertThat(ExamDateSheetService.timesOverlap(
                LocalTime.of(9, 0), null,
                LocalTime.of(9, 0), LocalTime.of(10, 0))).isFalse();
    }
}
