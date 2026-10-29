package com.erpschool.exam;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ExamDateSheetOverlapTest {

    @Test
    void overlappingWindowsAreRejected() {
        assertThat(ExamScheduleTimes.overlap(
                LocalTime.of(9, 0), LocalTime.of(11, 0),
                LocalTime.of(10, 0), LocalTime.of(12, 0))).isTrue();
    }

    @Test
    void identicalWindowsOverlap() {
        assertThat(ExamScheduleTimes.overlap(
                LocalTime.of(9, 0), LocalTime.of(11, 0),
                LocalTime.of(9, 0), LocalTime.of(11, 0))).isTrue();
    }

    @Test
    void disjointWindowsAreAllowed() {
        assertThat(ExamScheduleTimes.overlap(
                LocalTime.of(8, 0), LocalTime.of(9, 0),
                LocalTime.of(10, 0), LocalTime.of(11, 0))).isFalse();
    }

    @Test
    void adjacentWindowsAreAllowed() {
        assertThat(ExamScheduleTimes.overlap(
                LocalTime.of(9, 0), LocalTime.of(11, 0),
                LocalTime.of(11, 0), LocalTime.of(12, 0))).isFalse();
    }

    @Test
    void missingTimesDoNotOverlap() {
        assertThat(ExamScheduleTimes.overlap(
                LocalTime.of(9, 0), null,
                LocalTime.of(9, 0), LocalTime.of(10, 0))).isFalse();
    }

    @Test
    void invertedRangeIsNotTreatedAsOverlap() {
        assertThat(ExamScheduleTimes.overlap(
                LocalTime.of(11, 0), LocalTime.of(9, 0),
                LocalTime.of(10, 0), LocalTime.of(12, 0))).isFalse();
    }
}
