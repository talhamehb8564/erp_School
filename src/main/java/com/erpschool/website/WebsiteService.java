package com.erpschool.website;

import com.erpschool.campus.entity.Campus;
import com.erpschool.campus.repository.CampusRepository;
import com.erpschool.common.cache.CatalogCache;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.settings.entity.SchoolSettings;
import com.erpschool.settings.repository.SchoolSettingsRepository;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.entity.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebsiteService {

    private final TenantRepository tenantRepository;
    private final SchoolSettingsRepository settingsRepository;
    private final CatalogCache catalogCache;
    private final WebsiteInquiryRepository inquiryRepository;
    private final CampusRepository campusRepository;

    public WebsiteService(TenantRepository tenantRepository,
                          SchoolSettingsRepository settingsRepository,
                          CatalogCache catalogCache,
                          WebsiteInquiryRepository inquiryRepository,
                          CampusRepository campusRepository) {
        this.tenantRepository = tenantRepository;
        this.settingsRepository = settingsRepository;
        this.catalogCache = catalogCache;
        this.inquiryRepository = inquiryRepository;
        this.campusRepository = campusRepository;
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
        if (body.containsKey("whatsappUrl")) tenant.setWhatsappUrl(str(body, "whatsappUrl"));
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
        UUID pinned = TenantContext.getCampusId();
        UUID requested = uuid(body.get("campusId"));
        if (TenantContext.getRole() == UserRole.PRINCIPAL && pinned != null && requested != null && !pinned.equals(requested)) {
            throw new ForbiddenException("You can only edit the Principal profile for your campus");
        }
        UUID campusId = pinned != null ? pinned : requested;
        if (campusId == null) {
            throw new BusinessException("Select a campus for this Principal profile");
        }
        Campus campus = campusRepository.findById(campusId)
                .orElseThrow(() -> new ResourceNotFoundException("Campus", campusId));
        TenantGuard.assertSameTenant(campus.getTenantId());
        if (body.containsKey("principalName")) campus.setPrincipalName(str(body, "principalName"));
        if (body.containsKey("principalTitle")) campus.setPrincipalTitle(str(body, "principalTitle"));
        if (body.containsKey("principalPhotoUrl")) campus.setPrincipalPhotoUrl(str(body, "principalPhotoUrl"));
        if (body.containsKey("principalMessage")) campus.setPrincipalMessage(str(body, "principalMessage"));
        if (body.containsKey("principalPublished")) campus.setPrincipalPublished(truthy(body.get("principalPublished")));
        if (body.containsKey("principalPhotos")) {
            campus.setPrincipalPhotosJson(JsonLists.write(body.get("principalPhotos")));
        }
        List<Map<String, Object>> photos = JsonLists.publicMedia(campus.getPrincipalPhotosJson(), false);
        if (!photos.isEmpty()) {
            campus.setPrincipalPhotoUrl(String.valueOf(photos.get(0).get("imageUrl")));
        }
        campus.setUpdatedBy(TenantContext.getUserId());
        campusRepository.save(campus);
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
        m.put("whatsappUrl", tenant.getWhatsappUrl());
        m.put("gallery", JsonLists.read(tenant.getGalleryJson()));
        m.put("principalName", tenant.getPrincipalName());
        m.put("principalTitle", tenant.getPrincipalTitle());
        m.put("principalPhotoUrl", tenant.getPrincipalPhotoUrl());
        m.put("principalPhotos", JsonLists.read(tenant.getPrincipalPhotosJson()));
        m.put("principalMessage", tenant.getPrincipalMessage());
        m.put("principalPublished", tenant.isPrincipalPublished());
        List<Map<String, Object>> campuses = new ArrayList<>();
        for (Campus campus : campusRepository.findByTenantIdOrderByNameAsc(tenant.getId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", campus.getId());
            row.put("name", campus.getName());
            row.put("code", campus.getCode());
            row.put("principalName", campus.getPrincipalName());
            row.put("principalTitle", campus.getPrincipalTitle());
            row.put("principalPhotoUrl", campus.getPrincipalPhotoUrl());
            row.put("principalPhotos", JsonLists.read(campus.getPrincipalPhotosJson()));
            row.put("principalMessage", campus.getPrincipalMessage());
            row.put("principalPublished", campus.isPrincipalPublished());
            campuses.add(row);
        }
        m.put("campuses", campuses);
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

    private static UUID uuid(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
