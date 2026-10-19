package com.erpschool.feature;

import com.erpschool.common.cache.CatalogCache;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FeatureService {

    private static final long TTL_MS = 45_000;

    private final TenantFeatureRepository featureRepository;
    private final TenantRepository tenantRepository;
    private final CatalogCache catalogCache;
    private final ConcurrentHashMap<UUID, Cached> cache = new ConcurrentHashMap<>();

    public FeatureService(TenantFeatureRepository featureRepository,
                          TenantRepository tenantRepository,
                          CatalogCache catalogCache) {
        this.featureRepository = featureRepository;
        this.tenantRepository = tenantRepository;
        this.catalogCache = catalogCache;
    }

    public Map<SchoolFeature, Boolean> flags(UUID tenantId) {
        if (tenantId == null) {
            return defaults();
        }
        long now = System.currentTimeMillis();
        Cached hit = cache.get(tenantId);
        if (hit != null && hit.expiresAt > now) {
            return copy(hit.flags);
        }
        EnumMap<SchoolFeature, Boolean> flags = defaults();
        for (TenantFeature row : featureRepository.findByTenantId(tenantId)) {
            flags.put(row.getFeature(), row.isEnabled());
        }
        cache.put(tenantId, new Cached(copy(flags), now + TTL_MS));
        return flags;
    }

    public boolean enabled(UUID tenantId, SchoolFeature feature) {
        if (feature == null) {
            return true;
        }
        return Boolean.TRUE.equals(flags(tenantId).get(feature));
    }

    public Map<String, Boolean> current() {
        return toWire(flags(TenantContext.getTenantId()));
    }

    public Map<String, Boolean> snapshot(UUID tenantId) {
        return toWire(flags(tenantId));
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> forTenant(UUID tenantId) {
        requireTenant(tenantId);
        return toWire(flags(tenantId));
    }

    @Transactional
    public Map<String, Boolean> replace(UUID tenantId, Map<String, Boolean> body) {
        Tenant tenant = requireTenant(tenantId);
        if (body == null || body.isEmpty()) {
            return toWire(flags(tenantId));
        }
        Instant now = Instant.now();
        UUID actor = TenantContext.getUserId();
        Map<SchoolFeature, TenantFeature> existing = new EnumMap<>(SchoolFeature.class);
        for (TenantFeature row : featureRepository.findByTenantId(tenant.getId())) {
            existing.put(row.getFeature(), row);
        }
        for (Map.Entry<String, Boolean> entry : body.entrySet()) {
            SchoolFeature feature = parse(entry.getKey());
            if (feature == null || entry.getValue() == null) {
                continue;
            }
            TenantFeature row = existing.get(feature);
            if (row == null) {
                row = new TenantFeature();
                row.setTenantId(tenant.getId());
                row.setFeature(feature);
            }
            row.setEnabled(entry.getValue());
            row.setUpdatedAt(now);
            row.setUpdatedBy(actor);
            featureRepository.save(row);
        }
        cache.remove(tenantId);
        catalogCache.evictTenant(tenantId);
        return toWire(flags(tenantId));
    }

    public void evict(UUID tenantId) {
        if (tenantId == null) {
            cache.clear();
        } else {
            cache.remove(tenantId);
        }
    }

    static SchoolFeature parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SchoolFeature.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("School", tenantId));
    }

    private static EnumMap<SchoolFeature, Boolean> defaults() {
        EnumMap<SchoolFeature, Boolean> flags = new EnumMap<>(SchoolFeature.class);
        for (SchoolFeature feature : SchoolFeature.values()) {
            flags.put(feature, true);
        }
        return flags;
    }

    private static EnumMap<SchoolFeature, Boolean> copy(Map<SchoolFeature, Boolean> src) {
        return new EnumMap<>(src);
    }

    private static Map<String, Boolean> toWire(Map<SchoolFeature, Boolean> flags) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (SchoolFeature feature : SchoolFeature.values()) {
            out.put(feature.name(), Boolean.TRUE.equals(flags.get(feature)));
        }
        return out;
    }

    private record Cached(EnumMap<SchoolFeature, Boolean> flags, long expiresAt) {
    }
}
