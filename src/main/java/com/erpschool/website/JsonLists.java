package com.erpschool.website;

import com.erpschool.file.service.FileStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonLists {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern LOCALHOST_FILE_URL = Pattern.compile(
            "^https?://(?:localhost|127\\.0\\.0\\.1)(?::\\d+)?(/api/v1/files/.+)$",
            Pattern.CASE_INSENSITIVE
    );

    private JsonLists() {
    }

    public static List<Map<String, Object>> read(String raw) {
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

    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String normalizeFileUrl(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Matcher matcher = LOCALHOST_FILE_URL.matcher(trimmed);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return trimmed;
    }

    public static List<Map<String, Object>> publicMedia(String raw, boolean includeTitle) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : read(raw)) {
            Object url = row.get("imageUrl");
            String normalized = url == null ? null : normalizeFileUrl(url.toString());
            if (normalized == null || normalized.isBlank()) {
                continue;
            }
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("imageUrl", normalized);
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

    public static List<Map<String, Object>> normalizeAttachments(Object raw) {
        if (raw == null) {
            return List.of();
        }
        Collection<?> items;
        if (raw instanceof String s) {
            items = read(s);
        } else if (raw instanceof Collection<?> c) {
            items = c;
        } else {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        for (Object item : items) {
            Map<String, Object> entry = toAttachmentEntry(item);
            if (entry == null) {
                continue;
            }
            String fileUrl = String.valueOf(entry.get("fileUrl"));
            if (seenUrls.add(fileUrl)) {
                out.add(entry);
            }
        }
        return out;
    }

    public static List<Map<String, Object>> buildAttachments(String attachmentsJson, String... extraUrls) {
        List<Map<String, Object>> out = new ArrayList<>(normalizeAttachments(attachmentsJson));
        Set<String> seenUrls = new LinkedHashSet<>();
        for (Map<String, Object> row : out) {
            Object u = row.get("fileUrl");
            if (u != null) {
                seenUrls.add(u.toString());
            }
        }
        if (extraUrls != null) {
            for (String extra : extraUrls) {
                String normalizedUrl = normalizeFileUrl(extra);
                if (normalizedUrl == null || !seenUrls.add(normalizedUrl)) {
                    continue;
                }
                Map<String, Object> entry = toAttachmentEntry(Map.of("fileUrl", normalizedUrl));
                if (entry != null) {
                    out.add(entry);
                }
            }
        }
        return out;
    }

    private static Map<String, Object> toAttachmentEntry(Object item) {
        if (item == null) {
            return null;
        }
        String rawUrl = null;
        String rawName = null;
        String rawType = null;
        if (item instanceof String s) {
            rawUrl = s;
        } else if (item instanceof Map<?, ?> map) {
            rawUrl = firstNonBlank(map, "fileUrl", "url", "imageUrl", "href");
            rawName = firstNonBlank(map, "fileName", "name", "originalName", "title");
            rawType = firstNonBlank(map, "contentType", "type", "mimeType");
        }
        String fileUrl = normalizeFileUrl(rawUrl);
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        String fileName = (rawName != null && !rawName.isBlank()) ? rawName.trim() : deriveFileName(fileUrl);
        String contentType = FileStorageService.normalizeContentType(rawType, fileName + " " + fileUrl);
        if ("application/octet-stream".equals(contentType)) {
            String fromUrl = FileStorageService.inferFromName(stripQuery(fileUrl));
            if (!"application/octet-stream".equals(fromUrl)) {
                contentType = fromUrl;
            }
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("fileName", fileName);
        safe.put("fileUrl", fileUrl);
        safe.put("contentType", contentType);
        return safe;
    }

    private static String firstNonBlank(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString().trim();
            }
        }
        return null;
    }

    private static String stripQuery(String url) {
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    private static String deriveFileName(String url) {
        String clean = stripQuery(url);
        int slash = clean.lastIndexOf('/');
        String tail = slash >= 0 && slash + 1 < clean.length() ? clean.substring(slash + 1) : clean;
        // Strip leading UUID prefix (36 chars + '-') if present
        if (tail.length() > 37 && tail.charAt(8) == '-' && tail.charAt(13) == '-' && tail.charAt(36) == '-') {
            return tail.substring(37);
        }
        return tail.isBlank() ? "attachment" : tail;
    }
}
