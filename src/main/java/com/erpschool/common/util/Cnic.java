package com.erpschool.common.util;

import com.erpschool.common.exception.BusinessException;

public final class Cnic {

    private Cnic() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("CNIC / B-Form is required");
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() != 13) {
            throw new BusinessException("CNIC / B-Form must be exactly 13 digits");
        }
        return digits;
    }

    public static String format(String digits) {
        if (digits == null || digits.length() != 13) {
            return digits;
        }
        return digits.substring(0, 5) + "-" + digits.substring(5, 12) + "-" + digits.substring(12);
    }
}
