package com.erpschool.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;

/**
 * Neon transaction poolers reset connections that stay idle-in-transaction
 * (SQLState 08006). Seed steps retry the unit of work; callers must be idempotent.
 */
@Slf4j
@Component
public class SeedRetry {

    private static final int ATTEMPTS = 4;

    public void run(String label, Runnable work) {
        call(label, () -> {
            work.run();
            return null;
        });
    }

    public <T> T call(String label, java.util.function.Supplier<T> work) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return work.get();
            } catch (RuntimeException ex) {
                if (!isDisconnect(ex)) {
                    throw ex;
                }
                last = ex;
                log.warn("Seed step '{}' hit Neon disconnect (attempt {}/{}): {}",
                        label, attempt, ATTEMPTS, rootMessage(ex));
                sleep(400L * attempt);
            }
        }
        throw last;
    }

    static boolean isDisconnect(Throwable throwable) {
        Throwable t = throwable;
        while (t != null) {
            if (t instanceof SocketException || t instanceof SocketTimeoutException) {
                return true;
            }
            if (t instanceof SQLException sql) {
                String state = sql.getSQLState();
                if ("08006".equals(state) || "08001".equals(state) || "08003".equals(state) || "57P01".equals(state)) {
                    return true;
                }
            }
            String message = t.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("an i/o error occurred while sending to the backend")
                        || lower.contains("connection reset")
                        || lower.contains("connection is closed")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Seed retry interrupted", ie);
        }
    }
}
