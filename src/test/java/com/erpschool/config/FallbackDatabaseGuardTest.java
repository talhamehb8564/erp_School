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
        assertThat(next).contains("sslNegotiation=direct");
        assertThat(next).contains("prepareThreshold=0");
        assertThat(next).contains("preferQueryMode=simple");
        assertThat(next).contains("options=endpoint%3Dep-x");
    }

    @Test
    void poolerHostStripsPoolerSuffixFromEndpointOption() {
        String url = "jdbc:postgresql://ep-lively-rain-ay8mipsd-pooler.c-5.us-east-2.aws.neon.tech/neondb";
        String next = FallbackDatabaseGuard.normalizeJdbcUrl(url);
        assertThat(next).contains("options=endpoint%3Dep-lively-rain-ay8mipsd");
        assertThat(next).doesNotContain("endpoint%3Dep-lively-rain-ay8mipsd-pooler");
        assertThat(next).contains("sslNegotiation=direct");
    }

    @Test
    void normalizeJdbcUrlIsIdempotent() {
        String url = "jdbc:postgresql://host.neon.tech/neondb?sslmode=require&gssEncMode=disable"
                + "&sslNegotiation=direct&prepareThreshold=0&preferQueryMode=simple&tcpKeepAlive=true";
        assertThat(FallbackDatabaseGuard.normalizeJdbcUrl(url)).isEqualTo(url);
    }
}
