package com.erpschool.website;

import com.erpschool.admission.AdmissionService;
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
    private final AdmissionService admissionService;

    public PublicSchoolService(TenantRepository tenantRepository,
                               CampusRepository campusRepository,
                               CatalogCache catalogCache,
                               AdmissionService admissionService) {
        this.tenantRepository = tenantRepository;
        this.campusRepository = campusRepository;
        this.catalogCache = catalogCache;
        this.admissionService = admissionService;
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
        school.put("whatsappUrl", tenant.getWhatsappUrl());
        school.put("admissions", admissionService.publicSummary(tenant.getId()));
        school.put("gallery", JsonLists.publicMedia(tenant.getGalleryJson(), false));
        school.put("principal", principalView(
                tenant.getPrincipalName(),
                tenant.getPrincipalTitle(),
                tenant.getPrincipalPhotoUrl(),
                tenant.getPrincipalPhotosJson(),
                tenant.getPrincipalMessage(),
                tenant.isPrincipalPublished()));
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
            row.put("whatsappUrl", campus.getWhatsappUrl());
            row.put("hero", JsonLists.publicMedia(campus.getHeroJson(), true));
            Map<String, Object> campusPrincipal = principalView(
                    campus.getPrincipalName(),
                    campus.getPrincipalTitle(),
                    campus.getPrincipalPhotoUrl(),
                    campus.getPrincipalPhotosJson(),
                    campus.getPrincipalMessage(),
                    campus.isPrincipalPublished());
            row.put("principal", campusPrincipal);
            Map<String, Object> campusMap = new LinkedHashMap<>();
            campusMap.put("lat", campus.getMapLat());
            campusMap.put("lng", campus.getMapLng());
            campusMap.put("place", campus.getMapPlace());
            row.put("map", campusMap);
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

    private static Map<String, Object> principalView(String name, String title, String photo, String photosJson,
                                                     String message, boolean published) {
        java.util.List<Map<String, Object>> photos = JsonLists.publicMedia(photosJson, false);
        boolean custom = (message != null && !message.isBlank())
                || (name != null && !name.isBlank())
                || (photo != null && !photo.isBlank())
                || !photos.isEmpty();
        Map<String, Object> principal = new LinkedHashMap<>();
        principal.put("name", name);
        principal.put("title", title);
        String photoUrl = photo;
        if ((photoUrl == null || photoUrl.isBlank()) && !photos.isEmpty()) {
            Object first = photos.get(0).get("imageUrl");
            photoUrl = first == null ? null : first.toString();
        }
        principal.put("photoUrl", photoUrl);
        principal.put("photos", photos);
        principal.put("message", message);
        principal.put("published", published);
        principal.put("isDefault", !custom);
        return principal;
    }

    private Tenant resolve(String code) {
        if (code != null && !code.isBlank()) {
            return tenantRepository.findByCodeIgnoreCase(code.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("School", code));
        }
        return tenantRepository.findFirstByStatusInOrderByCreatedAtAsc(
                        List.of(TenantStatus.ACTIVE, TenantStatus.PENDING))
                .orElseThrow(() -> new ResourceNotFoundException("No school is configured yet"));
    }
}
