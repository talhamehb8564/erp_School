package com.erpschool.common.util;

import java.security.SecureRandom;

public final class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "@#$%";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    /**
     * Temporary password that satisfies: 10+ chars, upper, lower, digit, symbol.
     */
    public static String temporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        sb.append(pick(UPPER));
        sb.append(pick(LOWER));
        sb.append(pick(DIGITS));
        sb.append(pick(SYMBOLS));
        for (int i = 0; i < 8; i++) {
            sb.append(pick(ALL));
        }
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private static char pick(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }
}
