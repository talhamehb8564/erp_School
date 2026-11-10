package com.erpschool.common.util;

public final class StatusCopy {

    private StatusCopy() {
    }

    public static String studentInactiveReason(String code, String note) {
        String label = label(code, note);
        if (label == null) {
            return null;
        }
        if (note != null && !note.isBlank() && !"OTHER".equalsIgnoreCase(code)
                && !note.trim().equalsIgnoreCase(label)) {
            return label + " — " + note.trim();
        }
        return label;
    }

    public static String label(String code, String note) {
        if (code == null || code.isBlank()) {
            return (note == null || note.isBlank()) ? null : note.trim();
        }
        return switch (code.trim().toUpperCase()) {
            case "UNPAID_FEES" -> "Fee not paid";
            case "LEFT_SCHOOL", "TRANSFERRED" -> "Student has left the school";
            case "PARENT_REQUEST" -> "Deactivated at parent request";
            case "GRADUATED" -> "Graduated";
            case "ABSENT" -> "Student was absent";
            case "MEDICAL" -> "Medical reason";
            case "DISCIPLINARY" -> "Disciplinary action";
            case "INACTIVE" -> "Account is inactive";
            case "OTHER" -> (note == null || note.isBlank()) ? "Other" : note.trim();
            default -> code.replace('_', ' ');
        };
    }

    public static String loginInactiveMessage(String code, String note) {
        String reason = studentInactiveReason(code, note);
        if (reason == null) {
            return "Your account is currently inactive.";
        }
        return "Your account is currently inactive. Reason: " + reason;
    }

    public static String examExcludeReason(String code, String note) {
        return studentInactiveReason(code, note);
    }
}
