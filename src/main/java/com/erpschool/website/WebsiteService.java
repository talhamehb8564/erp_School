package com.erpschool.website;

import com.erpschool.common.cache.CatalogCache;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.settings.entity.SchoolSettings;
import com.erpschool.settings.repository.SchoolSettingsRepository;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class WebsiteService {

    private final TenantRepository tenantRepository;
    private final SchoolSettingsRepository settingsRepository;
    private final CatalogCache catalogCache;
    private final WebsiteInquiryRepository inquiryRepository;

    public WebsiteService(TenantRepository tenantRepository,
                          SchoolSettingsRepository settingsRepository,
                          CatalogCache catalogCache,
                          WebsiteInquiryRepository inquiryRepository) {
        this.tenantRepository = tenantRepository;
        this.settingsRepository = settingsRepository;
        this.catalogCache = catalogCache;
        this.inquiryRepository = inquiryRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get() {
        return view(requireTenant());
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
        if (body.containsKey("mapLat")) tenant.setMapLat(decimal(body.get("mapLat")));
        if (body.containsKey("mapLng")) tenant.setMapLng(decimal(body.get("mapLng")));
        if (body.containsKey("mapPlace")) tenant.setMapPlace(str(body, "mapPlace"));
        if (body.containsKey("noticeTitle")) tenant.setNoticeTitle(str(body, "noticeTitle"));
        if (body.containsKey("noticeBody")) tenant.setNoticeBody(str(body, "noticeBody"));
        if (body.containsKey("noticeVideoUrl")) tenant.setNoticeVideoUrl(str(body, "noticeVideoUrl"));
        if (body.containsKey("noticePosterUrl")) tenant.setNoticePosterUrl(str(body, "noticePosterUrl"));
        if (body.containsKey("noticePublished")) tenant.setNoticePublished(truthy(body.get("noticePublished")));
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
        return view(tenant);
    }

    @Transactional
    public Map<String, Object> savePrincipal(Map<String, Object> body) {
        Tenant tenant = requireTenant();
        if (body.containsKey("principalName")) tenant.setPrincipalName(str(body, "principalName"));
        if (body.containsKey("principalTitle")) tenant.setPrincipalTitle(str(body, "principalTitle"));
        if (body.containsKey("principalPhotoUrl")) tenant.setPrincipalPhotoUrl(str(body, "principalPhotoUrl"));
        if (body.containsKey("principalMessage")) tenant.setPrincipalMessage(str(body, "principalMessage"));
        if (body.containsKey("principalPublished")) tenant.setPrincipalPublished(truthy(body.get("principalPublished")));
        tenant.setUpdatedBy(TenantContext.getUserId());
        tenantRepository.save(tenant);
        catalogCache.evictTenant(tenant.getId());
        return view(tenant);
    }

    @Transactional
    public Map<String, Object> submitInquiry(Tenant tenant, Map<String, Object> body) {
        if (tenant == null) {
            throw new ResourceNotFoundException("No school is configured yet");
        }
        if (!blank(str(body, "company")) || !blank(str(body, "website"))) {
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("accepted", true);
            return ok;
        }
        String name = str(body, "name");
        String email = str(body, "email");
        String message = str(body, "message");
        if (blank(name)) throw new BusinessException("Name is required");
        if (blank(email) || !email.contains("@") || email.length() > 150) {
            throw new BusinessException("A valid email is required");
        }
        if (blank(message) || message.length() < 8) {
            throw new BusinessException("Please write a short message");
        }
        if (message.length() > 4000) {
            throw new BusinessException("Message is too long");
        }
        WebsiteInquiry row = new WebsiteInquiry();
        row.setTenantId(tenant.getId());
        row.setName(name);
        row.setEmail(email);
        row.setPhone(str(body, "phone"));
        row.setMessage(message);
        inquiryRepository.save(row);
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("accepted", true);
        ok.put("message", "Thank you. The school has received your message.");
        return ok;
    }

    private Map<String, Object> view(Tenant tenant) {
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
        m.put("principalName", tenant.getPrincipalName());
        m.put("principalTitle", tenant.getPrincipalTitle());
        m.put("principalPhotoUrl", tenant.getPrincipalPhotoUrl());
        m.put("principalMessage", tenant.getPrincipalMessage());
        m.put("principalPublished", tenant.isPrincipalPublished());
        m.put("mapLat", tenant.getMapLat());
        m.put("mapLng", tenant.getMapLng());
        m.put("mapPlace", tenant.getMapPlace());
        m.put("noticeTitle", tenant.getNoticeTitle());
        m.put("noticeBody", tenant.getNoticeBody());
        m.put("noticeVideoUrl", tenant.getNoticeVideoUrl());
        m.put("noticePosterUrl", tenant.getNoticePosterUrl());
        m.put("noticePublished", tenant.isNoticePublished());
        return m;
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

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean truthy(Object v) {
        if (v instanceof Boolean b) return b;
        if (v == null) return false;
        return "true".equalsIgnoreCase(v.toString()) || "1".equals(v.toString());
    }

    private static BigDecimal decimal(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        try {
            return new BigDecimal(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
