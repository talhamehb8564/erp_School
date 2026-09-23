package com.erpschool.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fail fast if the datasource is missing, unauthenticated, or an in-memory fallback.
 * Also normalizes Neon JDBC parameters: IPv4, sslmode=require, gssEncMode=disable
 * (pgjdbc GSSENCRequest is a common cause of SQLState 08001 / Connection reset).
 */
public class FallbackDatabaseGuard implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        System.setProperty("java.net.preferIPv4Stack", "true");

        String url = environment.getProperty("spring.datasource.url", "");
        String password = environment.getProperty("spring.datasource.password", "");
        String lower = url.toLowerCase();

        if (url.isBlank()) {
            throw new IllegalStateException(
                    "spring.datasource.url is required. Set DB_URL or SPRING_DATASOURCE_URL to the Neon JDBC URL.");
        }
        if (lower.contains("h2:") || lower.contains("sqlite") || lower.contains(":mem:")
                || lower.contains("pglite") || lower.contains("hsqldb")) {
            throw new IllegalStateException(
                    "In-memory/fallback databases are disabled. Neon PostgreSQL is required. Refusing URL: " + url);
        }
        if (!lower.contains("postgresql") && !lower.contains("postgres")) {
            throw new IllegalStateException(
                    "Datasource must be PostgreSQL (Neon). Refusing URL: " + url);
        }
        boolean testProfile = Arrays.asList(environment.getActiveProfiles()).contains("test")
                || environment.getProperty("spring.profiles.active", "").contains("test");
        if (!testProfile && !lower.contains("neon.tech")) {
            throw new IllegalStateException(
                    "Datasource URL must target Neon PostgreSQL (host containing neon.tech). Refusing URL: " + url);
        }
        if (!testProfile && password.isBlank()) {
            throw new IllegalStateException(
                    "Neon password is not set. Export PGPASSWORD or DB_PASSWORD or SPRING_DATASOURCE_PASSWORD. "
                            + "Do not commit the password to Git.");
        }

        String adjusted = normalizeJdbcUrl(url);
        if (!adjusted.equals(url)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("spring.datasource.url", adjusted);
            environment.getPropertySources().addFirst(new MapPropertySource("erpNeonJdbc", map));
        }
    }

    static String normalizeJdbcUrl(String url) {
        String next = stripQueryParam(url, "channelBinding");
        next = stripQueryParam(next, "channel_binding");
        next = ensureQueryParam(next, "sslmode", "require");
        next = ensureQueryParam(next, "gssEncMode", "disable");
        next = ensureQueryParam(next, "prepareThreshold", "0");
        return next;
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
