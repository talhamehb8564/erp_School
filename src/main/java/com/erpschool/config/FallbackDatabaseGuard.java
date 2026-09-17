package com.erpschool.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;

/**
 * Fail fast if the datasource is missing, unauthenticated, or an in-memory fallback.
 * This backend is required to use Neon PostgreSQL.
 */
public class FallbackDatabaseGuard implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
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
        if (lower.contains("channelbinding=require")) {
            throw new IllegalStateException(
                    "channelBinding=require is not allowed on the JDBC URL until Neon connectivity is proven. "
                            + "Use sslmode=require only.");
        }
    }
}
