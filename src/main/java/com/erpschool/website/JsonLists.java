package com.erpschool.website;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonLists {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonLists() {
    }

    static List<Map<String, Object>> read(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = MAPPER.readValue(raw, new TypeReference<>() {
            });
            return rows == null ? List.of() : rows;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    static String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    static List<Map<String, Object>> publicMedia(String raw, boolean includeTitle) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : read(raw)) {
            Object url = row.get("imageUrl");
            if (url == null || url.toString().isBlank()) {
                continue;
            }
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("imageUrl", url.toString());
            if (row.get("caption") != null) {
                safe.put("caption", row.get("caption").toString());
            }
            if (includeTitle && row.get("title") != null) {
                safe.put("title", row.get("title").toString());
            }
            out.add(safe);
        }
        return out;
    }
}
