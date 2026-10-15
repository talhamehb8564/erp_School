package com.erpschool.website;

import com.erpschool.campus.entity.Campus;
import com.erpschool.campus.repository.CampusRepository;
import com.erpschool.common.cache.CatalogCache;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.entity.TenantStatus;
import com.erpschool.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PublicSchoolService {

    private final TenantRepository tenantRepository;
    private final CampusRepository campusRepository;
    private final CatalogCache catalogCache;

    public PublicSchoolService(TenantRepository tenantRepository,
                               CampusRepository campusRepository,
                               CatalogCache catalogCache) {
        this.tenantRepository = tenantRepository;
        this.campusRepository = campusRepository;
        this.catalogCache = catalogCache;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> branding(String code) {
        String key = "public:school:" + (code == null || code.isBlank() ? "default" : code.trim().toLowerCase());
        return catalogCache.get(key, () -> load(code));
    }

    private Map<String, Object> load(String code) {
        Tenant tenant = resolve(code);
        Map<String, Object> school = new LinkedHashMap<>();
        school.put("id", tenant.getId());
        school.put("code", tenant.getCode());
        school.put("name", tenant.getName());
        school.put("logoUrl", tenant.getLogoUrl());
        school.put("primaryColor", tenant.getPrimaryColor());
        school.put("accentColor", tenant.getAccentColor());
        school.put("secondaryColor", tenant.getSecondaryColor());
        school.put("tagline", tenant.getTagline());
        school.put("aboutText", tenant.getAboutText());
        school.put("academicsText", tenant.getAcademicsText());
        school.put("achievementsText", tenant.getAchievementsText());
        school.put("email", tenant.getEmail());
        school.put("phone", tenant.getPhone());
        school.put("addressLine", tenant.getAddressLine());
        school.put("city", tenant.getCity());
        school.put("website", tenant.getWebsite());
        school.put("facebookUrl", tenant.getFacebookUrl());
        school.put("instagramUrl", tenant.getInstagramUrl());
        school.put("youtubeUrl", tenant.getYoutubeUrl());
        school.put("twitterUrl", tenant.getTwitterUrl());
        school.put("linkedinUrl", tenant.getLinkedinUrl());
        school.put("gallery", JsonLists.publicMedia(tenant.getGalleryJson(), false));
        boolean principalCustom = tenant.getPrincipalMessage() != null && !tenant.getPrincipalMessage().isBlank();
        Map<String, Object> principal = new LinkedHashMap<>();
        principal.put("name", tenant.getPrincipalName());
        principal.put("title", tenant.getPrincipalTitle());
        principal.put("photoUrl", tenant.getPrincipalPhotoUrl());
        principal.put("message", tenant.getPrincipalMessage());
        principal.put("published", tenant.isPrincipalPublished());
        principal.put("isDefault", !principalCustom);
        school.put("principal", principal);
        boolean noticeCustom = tenant.isNoticePublished()
                && tenant.getNoticeTitle() != null && !tenant.getNoticeTitle().isBlank();
        Map<String, Object> notice = new LinkedHashMap<>();
        notice.put("title", tenant.getNoticeTitle());
        notice.put("body", tenant.getNoticeBody());
        notice.put("videoUrl", tenant.getNoticeVideoUrl());
        notice.put("posterUrl", tenant.getNoticePosterUrl());
        notice.put("published", tenant.isNoticePublished());
        notice.put("isDefault", !noticeCustom);
        school.put("notice", notice);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lat", tenant.getMapLat());
        map.put("lng", tenant.getMapLng());
        map.put("place", tenant.getMapPlace());
        school.put("map", map);

        List<Map<String, Object>> campuses = new ArrayList<>();
        for (Campus campus : campusRepository.findByTenantIdOrderByNameAsc(tenant.getId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", campus.getId());
            row.put("name", campus.getName());
            row.put("code", campus.getCode());
            row.put("address", campus.getAddress());
            row.put("city", campus.getCity());
            row.put("phone", campus.getPhone());
            row.put("email", campus.getEmail());
            row.put("facebookUrl", campus.getFacebookUrl());
            row.put("instagramUrl", campus.getInstagramUrl());
            row.put("youtubeUrl", campus.getYoutubeUrl());
            row.put("twitterUrl", campus.getTwitterUrl());
            row.put("hero", JsonLists.publicMedia(campus.getHeroJson(), true));
            campuses.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("school", school);
        out.put("campuses", campuses);
        return out;
    }

    Tenant resolvePublic(String code) {
        return resolve(code);
    }

    private Tenant resolve(String code) {
        if (code != null && !code.isBlank()) {
            return tenantRepository.findByCodeIgnoreCase(code.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("School", code));
        }
        List<Tenant> active = tenantRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(TenantStatus.ACTIVE, TenantStatus.PENDING));
        if (active.isEmpty()) {
            throw new ResourceNotFoundException("No school is configured yet");
        }
        return active.get(0);
    }
}
