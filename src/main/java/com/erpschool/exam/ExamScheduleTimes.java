package com.erpschool.exam;

import java.time.LocalTime;

/**
 * Compares school-local exam windows. Adjacent periods (end == next start) do
 * not overlap. Invalid or incomplete ranges are not treated as collisions;
 * callers reject those separately.
 */
public final class ExamScheduleTimes {

    private ExamScheduleTimes() {
    }

    public static boolean overlap(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
        if (startA == null || endA == null || startB == null || endB == null) {
            return false;
        }
        return startA.isBefore(endA) && startB.isBefore(endB)
                && startA.isBefore(endB) && startB.isBefore(endA);
    }
}
