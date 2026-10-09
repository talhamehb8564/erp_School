package com.erpschool.common.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Parses exam announcement timestamps from ISO-8601 or datetime-local values. */
public final class Instants {

    private static final ZoneId KARACHI = ZoneId.of("Asia/Karachi");
    private static final DateTimeFormatter LOCAL_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter LOCAL_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private Instants() {
    }

    public static Instant parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        for (DateTimeFormatter formatter : new DateTimeFormatter[] { DateTimeFormatter.ISO_LOCAL_DATE_TIME, LOCAL_SECONDS, LOCAL_MINUTES }) {
            try {
                return LocalDateTime.parse(value, formatter).atZone(KARACHI).toInstant();
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new IllegalArgumentException("Invalid date-time: " + raw);
    }
}
