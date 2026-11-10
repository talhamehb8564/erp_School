package com.erpschool.settings;

import java.util.List;
import java.util.Set;

public final class PortalFonts {

    public static final String DEFAULT = "Outfit";

    public static final List<String> OPTIONS = List.of(
            "Outfit",
            "Inter",
            "Roboto",
            "Open Sans",
            "Lato",
            "Poppins",
            "Nunito",
            "Montserrat",
            "Source Sans 3",
            "Atkinson Hyperlegible",
            "Times New Roman",
            "Georgia",
            "Arial",
            "Fraunces",
            "Literata",
            "Merriweather"
    );

    private static final Set<String> ALLOWED = Set.copyOf(OPTIONS);

    private PortalFonts() {
    }

    public static boolean allowed(String raw) {
        return raw != null && ALLOWED.contains(raw.trim());
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String t = raw.trim();
        return ALLOWED.contains(t) ? t : DEFAULT;
    }
}
