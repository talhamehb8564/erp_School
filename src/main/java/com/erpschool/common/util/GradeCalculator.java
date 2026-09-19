package com.erpschool.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class GradeCalculator {

    private GradeCalculator() {
    }

    public static BigDecimal percentage(BigDecimal obtained, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return obtained.multiply(BigDecimal.valueOf(100))
                .divide(total, 2, RoundingMode.HALF_UP);
    }

    public static String grade(BigDecimal percentage) {
        double p = percentage.doubleValue();
        if (p >= 90) return "A+";
        if (p >= 80) return "A";
        if (p >= 70) return "B";
        if (p >= 60) return "C";
        if (p >= 40) return "D";
        return "F";
    }

    public static String passStatus(BigDecimal percentage) {
        return percentage.compareTo(BigDecimal.valueOf(40)) >= 0 ? "PASS" : "FAIL";
    }
}
