package com.erpschool.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fail fast if the datasource is missing or an in-memory fallback.
 * Strips channelBinding=require. TLS pooler query flags are added only when the
 * host looks like a serverless pooler; local PostgreSQL URLs are left unchanged.
 */
public class FallbackDatabaseGuard implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("spring.datasource.url", "");
        String lower = url.toLowerCase();

        if (url.isBlank()) {
            throw new IllegalStateException(
                    "spring.datasource.url is required. Set SPRING_DATASOURCE_URL or DB_URL.");
        }
        if (lower.contains("h2:") || lower.contains("sqlite") || lower.contains(":mem:")
                || lower.contains("pglite") || lower.contains("hsqldb")) {
            throw new IllegalStateException(
                    "In-memory/fallback databases are disabled. Configure PostgreSQL through environment variables.");
        }
        if (!lower.contains("postgresql") && !lower.contains("postgres")) {
            throw new IllegalStateException("Datasource must be PostgreSQL.");
        }

        String adjusted = normalizeJdbcUrl(url);
        if (!adjusted.equals(url)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("spring.datasource.url", adjusted);
            environment.getPropertySources().addFirst(new MapPropertySource("erpJdbcNormalize", map));
        }
    }

    static String normalizeJdbcUrl(String url) {
        String next = stripQueryParam(url, "channelBinding");
        next = stripQueryParam(next, "channel_binding");
        if (!hostLooksLikeTlsPooler(next)) {
            return next;
        }
        next = ensureQueryParam(next, "sslmode", "require");
        next = ensureQueryParam(next, "gssEncMode", "disable");
        next = ensureQueryParam(next, "sslNegotiation", "direct");
        next = ensureQueryParam(next, "prepareThreshold", "0");
        next = ensureQueryParam(next, "preferQueryMode", "simple");
        next = ensureQueryParam(next, "tcpKeepAlive", "true");
        String endpoint = poolerEndpointId(next);
        if (endpoint != null && !next.matches("(?i).*[?&]options=.*")) {
            next = next + (next.contains("?") ? "&" : "?") + "options=endpoint%3D" + endpoint;
        }
        return next;
    }

    static String poolerEndpointId(String url) {
        String host = hostOf(url);
        if (host == null) {
            return null;
        }
        String label = host.split("\\.")[0];
        if (!label.startsWith("ep-")) {
            return null;
        }
        if (label.endsWith("-pooler")) {
            label = label.substring(0, label.length() - "-pooler".length());
        }
        return label;
    }

    private static boolean hostLooksLikeTlsPooler(String url) {
        String host = hostOf(url);
        if (host == null) {
            return false;
        }
        String label = host.split("\\.")[0];
        return label.startsWith("ep-") || host.contains(".pooler.");
    }

    private static String hostOf(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return null;
        }
        int hostStart = scheme + 3;
        int slash = url.indexOf('/', hostStart);
        int query = url.indexOf('?', hostStart);
        int hostEnd = url.length();
        if (slash >= 0) {
            hostEnd = Math.min(hostEnd, slash);
        }
        if (query >= 0) {
            hostEnd = Math.min(hostEnd, query);
        }
        String hostPort = url.substring(hostStart, hostEnd);
        if (hostPort.startsWith("[")) {
            return null;
        }
        int at = hostPort.lastIndexOf('@');
        if (at >= 0) {
            hostPort = hostPort.substring(at + 1);
        }
        int colon = hostPort.lastIndexOf(':');
        return colon > 0 ? hostPort.substring(0, colon) : hostPort;
    }

    private static String stripQueryParam(String url, String key) {
        return url.replaceAll("(?i)([?&])" + key + "=[^&]*", "$1")
                .replace("?&", "?")
                .replaceAll("[?&]$", "")
                .replace("&&", "&");
    }

    private static String ensureQueryParam(String url, String key, String value) {
        if (url.matches("(?i).*[?&]" + key + "=.*")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + key + "=" + value;
    }
}
