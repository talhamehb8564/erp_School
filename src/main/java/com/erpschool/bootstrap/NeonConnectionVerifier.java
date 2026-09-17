package com.erpschool.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Proves the running process is connected to Neon PostgreSQL after Flyway.
 * If this bean cannot query Neon, startup fails — it never switches database.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NeonConnectionVerifier implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    public NeonConnectionVerifier(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String url = environment.getProperty("spring.datasource.url", "");
        log.info("Verifying live PostgreSQL connection. JDBC URL (no password): {}", url);

        Map<String, Object> identity = jdbcTemplate.queryForMap("""
                SELECT current_database() AS database,
                       current_user AS db_user,
                       inet_server_addr()::text AS server_addr,
                       inet_server_port() AS server_port,
                       version() AS version
                """);
        log.info("PostgreSQL identity: {}", identity);

        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                ORDER BY table_name
                """, String.class);
        log.info("Public tables in Neon: {}", tables);

        List<Map<String, Object>> history = jdbcTemplate.queryForList("""
                SELECT installed_rank, version, description, success
                FROM flyway_schema_history
                ORDER BY installed_rank
                """);
        log.info("flyway_schema_history: {}", history);

        requireTable(tables, "tenants");
        requireTable(tables, "users");
        requireTable(tables, "username_sequences");
        requireTable(tables, "refresh_tokens");
        requireTable(tables, "audit_logs");
        requireTable(tables, "flyway_schema_history");

        boolean v1 = history.stream().anyMatch(row ->
                "1".equals(String.valueOf(row.get("version")))
                        && Boolean.TRUE.equals(row.get("success")));
        if (!v1) {
            throw new IllegalStateException(
                    "Flyway did not record a successful V1 migration on Neon. History=" + history);
        }
        log.info("Neon connection and Flyway V1 verification succeeded. tables={}", tables);
    }

    private static void requireTable(List<String> tables, String name) {
        if (!tables.contains(name)) {
            throw new IllegalStateException(
                    "Required table '" + name + "' is missing in Neon public schema. Found: " + tables);
        }
    }
}
