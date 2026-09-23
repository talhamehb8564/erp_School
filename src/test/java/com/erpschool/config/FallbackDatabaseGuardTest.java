package com.erpschool.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackDatabaseGuardTest {

    @Test
    void normalizeJdbcUrlDisablesGssAndChannelBinding() {
        String url = "jdbc:postgresql://ep-x.c-5.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require";
        String next = FallbackDatabaseGuard.normalizeJdbcUrl(url);
        assertThat(next).doesNotContainIgnoringCase("channelBinding");
        assertThat(next).contains("sslmode=require");
        assertThat(next).contains("gssEncMode=disable");
        assertThat(next).contains("prepareThreshold=0");
    }

    @Test
    void normalizeJdbcUrlIsIdempotent() {
        String url = "jdbc:postgresql://host.neon.tech/neondb?sslmode=require&gssEncMode=disable&prepareThreshold=0";
        assertThat(FallbackDatabaseGuard.normalizeJdbcUrl(url)).isEqualTo(url);
    }
}
