package com.erpschool.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackDatabaseGuardTest {

    @Test
    void localPostgresUrlIsUnchangedExceptChannelBinding() {
        String url = "jdbc:postgresql://localhost:5432/erp_school?channelBinding=require";
        String next = FallbackDatabaseGuard.normalizeJdbcUrl(url);
        assertThat(next).isEqualTo("jdbc:postgresql://localhost:5432/erp_school");
        assertThat(next).doesNotContainIgnoringCase("channelBinding");
        assertThat(next).doesNotContain("sslmode=require");
    }

    @Test
    void poolerHostGetsTlsFlagsAndEndpointOption() {
        String url = "jdbc:postgresql://ep-demo-pooler.example.com/school?sslmode=require&channelBinding=require";
        String next = FallbackDatabaseGuard.normalizeJdbcUrl(url);
        assertThat(next).doesNotContainIgnoringCase("channelBinding");
        assertThat(next).contains("sslmode=require");
        assertThat(next).contains("gssEncMode=disable");
        assertThat(next).contains("sslNegotiation=direct");
        assertThat(next).contains("options=endpoint%3Dep-demo");
        assertThat(next).doesNotContain("endpoint%3Dep-demo-pooler");
    }

    @Test
    void normalizeJdbcUrlIsIdempotentForLocal() {
        String url = "jdbc:postgresql://localhost:5432/erp_school";
        assertThat(FallbackDatabaseGuard.normalizeJdbcUrl(url)).isEqualTo(url);
    }
}
