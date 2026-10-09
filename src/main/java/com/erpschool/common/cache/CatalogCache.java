package com.erpschool.common.cache;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Short-lived, tenant-keyed cache for branding and academic catalogues.
 * Never stores user-specific or financial data.
 */
@Component
public class CatalogCache {

    private static final long TTL_MS = Duration.ofSeconds(45).toMillis();

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        Entry hit = map.get(key);
        if (hit != null && hit.expiresAt > now) {
            return (T) hit.value;
        }
        T value = loader.get();
        map.put(key, new Entry(value, now + TTL_MS));
        return value;
    }

    public void evictTenant(UUID tenantId) {
        if (tenantId == null) {
            return;
        }
        String prefix = tenantId + ":";
        map.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private record Entry(Object value, long expiresAt) {
    }
}
