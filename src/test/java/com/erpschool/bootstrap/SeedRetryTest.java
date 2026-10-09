package com.erpschool.bootstrap;

import org.junit.jupiter.api.Test;

import java.net.SocketException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedRetryTest {

    private final SeedRetry seedRetry = new SeedRetry();

    @Test
    void retriesNeonDisconnectThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        seedRetry.run("demo-admin", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw wrap08006();
            }
        });
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryBusinessErrors() {
        assertThatThrownBy(() -> seedRetry.run("demo-admin", () -> {
            throw new IllegalStateException("Email is already in use");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email is already in use");
    }

    @Test
    void detects08006AndConnectionReset() {
        assertThat(SeedRetry.isDisconnect(wrap08006())).isTrue();
        assertThat(SeedRetry.isDisconnect(new RuntimeException(new SocketException("Connection reset")))).isTrue();
        assertThat(SeedRetry.isDisconnect(new IllegalStateException("nope"))).isFalse();
    }

    private static RuntimeException wrap08006() {
        SQLException sql = new SQLException(
                "An I/O error occurred while sending to the backend", "08006",
                new SocketException("Connection reset"));
        return new RuntimeException("Unable to flush", sql);
    }
}
