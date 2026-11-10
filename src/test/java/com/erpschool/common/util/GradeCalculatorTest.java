package com.erpschool.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GradeCalculatorTest {

    @Test
    void percentageAndGrade() {
        assertThat(GradeCalculator.percentage(new BigDecimal("85"), new BigDecimal("100")))
                .isEqualByComparingTo("85.00");
        assertThat(GradeCalculator.grade(new BigDecimal("92"))).isEqualTo("A+");
        assertThat(GradeCalculator.grade(new BigDecimal("81"))).isEqualTo("A");
        assertThat(GradeCalculator.grade(new BigDecimal("39"))).isEqualTo("F");
        assertThat(GradeCalculator.passStatus(new BigDecimal("40"))).isEqualTo("PASS");
        assertThat(GradeCalculator.passStatus(new BigDecimal("39.99"))).isEqualTo("FAIL");
    }

    @Test
    void zeroTotalIsZeroPercent() {
        assertThat(GradeCalculator.percentage(new BigDecimal("10"), BigDecimal.ZERO))
                .isEqualByComparingTo("0.00");
    }
}
