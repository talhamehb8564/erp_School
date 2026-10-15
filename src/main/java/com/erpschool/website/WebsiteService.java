package com.erpschool.website;

import com.erpschool.common.cache.CatalogCache;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.settings.entity.SchoolSettings;
import com.erpschool.settings.repository.SchoolSettingsRepository;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class WebsiteService {

    private final TenantRepository tenantRepository;
    private final SchoolSettingsRepository settingsRepository;
    private final CatalogCache catalogCache;

    public WebsiteService(TenantRepository tenantRepository,
                          SchoolSettingsRepository settingsRepository,
                          CatalogCache catalogCache) {
        this.tenantRepository = tenantRepository;
        this.settingsRepository = settingsRepository;
        this.catalogCache = catalogCache;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get() {
        Tenant tenant = requireTenant();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", tenant.getName());
        m.put("logoUrl", tenant.getLogoUrl());
        m.put("primaryColor", tenant.getPrimaryColor());
        m.put("accentColor", tenant.getAccentColor());
        m.put("secondaryColor", tenant.getSecondaryColor());
        m.put("tagline", tenant.getTagline());
        m.put("aboutText", tenant.getAboutText());
        m.put("academicsText", tenant.getAcademicsText());
        m.put("achievementsText", tenant.getAchievementsText());
        m.put("email", tenant.getEmail());
        m.put("phone", tenant.getPhone());
        m.put("addressLine", tenant.getAddressLine());
        m.put("city", tenant.getCity());
        m.put("website", tenant.getWebsite());
        m.put("facebookUrl", tenant.getFacebookUrl());
        m.put("instagramUrl", tenant.getInstagramUrl());
        m.put("youtubeUrl", tenant.getYoutubeUrl());
        m.put("twitterUrl", tenant.getTwitterUrl());
        m.put("linkedinUrl", tenant.getLinkedinUrl());
        m.put("gallery", JsonLists.read(tenant.getGalleryJson()));
        return m;
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> body) {
        Tenant tenant = requireTenant();
        if (str(body, "name") != null) tenant.setName(str(body, "name"));
        if (body.containsKey("logoUrl")) tenant.setLogoUrl(str(body, "logoUrl"));
        if (body.containsKey("primaryColor")) tenant.setPrimaryColor(str(body, "primaryColor"));
        if (body.containsKey("accentColor")) tenant.setAccentColor(str(body, "accentColor"));
        if (body.containsKey("secondaryColor")) tenant.setSecondaryColor(str(body, "secondaryColor"));
        if (body.containsKey("tagline")) tenant.setTagline(str(body, "tagline"));
        if (body.containsKey("aboutText")) tenant.setAboutText(str(body, "aboutText"));
        if (body.containsKey("academicsText")) tenant.setAcademicsText(str(body, "academicsText"));
        if (body.containsKey("achievementsText")) tenant.setAchievementsText(str(body, "achievementsText"));
        if (body.containsKey("email")) tenant.setEmail(str(body, "email"));
        if (body.containsKey("phone")) tenant.setPhone(str(body, "phone"));
        if (body.containsKey("addressLine")) tenant.setAddressLine(str(body, "addressLine"));
        if (body.containsKey("city")) tenant.setCity(str(body, "city"));
        if (body.containsKey("website")) tenant.setWebsite(str(body, "website"));
        if (body.containsKey("facebookUrl")) tenant.setFacebookUrl(str(body, "facebookUrl"));
        if (body.containsKey("instagramUrl")) tenant.setInstagramUrl(str(body, "instagramUrl"));
        if (body.containsKey("youtubeUrl")) tenant.setYoutubeUrl(str(body, "youtubeUrl"));
        if (body.containsKey("twitterUrl")) tenant.setTwitterUrl(str(body, "twitterUrl"));
        if (body.containsKey("linkedinUrl")) tenant.setLinkedinUrl(str(body, "linkedinUrl"));
        if (body.containsKey("gallery")) tenant.setGalleryJson(JsonLists.write(body.get("gallery")));
        tenant.setUpdatedBy(TenantContext.getUserId());
        tenantRepository.save(tenant);

        UUID tenantId = tenant.getId();
        SchoolSettings s = settingsRepository.findById(tenantId).orElseGet(SchoolSettings::new);
        if (s.getTenantId() == null) {
            s.setTenantId(tenantId);
            s.setCreatedAt(Instant.now());
            s.setCreatedBy(TenantContext.getUserId());
        }
        if (tenant.getLogoUrl() != null) s.setLogoUrl(tenant.getLogoUrl());
        if (tenant.getPrimaryColor() != null) s.setPrimaryColor(tenant.getPrimaryColor());
        if (tenant.getAccentColor() != null) s.setAccentColor(tenant.getAccentColor());
        if (tenant.getSecondaryColor() != null) s.setSecondaryColor(tenant.getSecondaryColor());
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(TenantContext.getUserId());
        settingsRepository.save(s);
        catalogCache.evictTenant(tenantId);
        return get();
    }

    private Tenant requireTenant() {
        UUID tenantId = TenantGuard.requireTenantId(null);
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
