package com.erpschool.tenant.entity;

import com.erpschool.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(name = "address_line", length = 300)
    private String addressLine;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(nullable = false, length = 100)
    private String country = "Pakistan";

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "primary_color", length = 16)
    private String primaryColor;

    @Column(name = "accent_color", length = 16)
    private String accentColor;

    @Column(name = "secondary_color", length = 16)
    private String secondaryColor;

    @Column(length = 300)
    private String tagline;

    @Column(name = "about_text", columnDefinition = "TEXT")
    private String aboutText;

    @Column(name = "academics_text", columnDefinition = "TEXT")
    private String academicsText;

    @Column(name = "achievements_text", columnDefinition = "TEXT")
    private String achievementsText;

    @Column(name = "facebook_url", length = 300)
    private String facebookUrl;

    @Column(name = "instagram_url", length = 300)
    private String instagramUrl;

    @Column(name = "youtube_url", length = 300)
    private String youtubeUrl;

    @Column(name = "twitter_url", length = 300)
    private String twitterUrl;

    @Column(name = "linkedin_url", length = 300)
    private String linkedinUrl;

    @Column(name = "whatsapp_url", length = 300)
    private String whatsappUrl;

    @Column(name = "gallery_json", columnDefinition = "TEXT")
    private String galleryJson;

    @Column(name = "principal_name", length = 200)
    private String principalName;

    @Column(name = "principal_title", length = 120)
    private String principalTitle;

    @Column(name = "principal_photo_url", length = 500)
    private String principalPhotoUrl;

    @Column(name = "principal_photos_json", columnDefinition = "TEXT")
    private String principalPhotosJson;

    @Column(name = "principal_message", columnDefinition = "TEXT")
    private String principalMessage;

    @Column(name = "principal_published", nullable = false)
    private boolean principalPublished = true;

    @Column(name = "map_lat", precision = 10, scale = 7)
    private java.math.BigDecimal mapLat;

    @Column(name = "map_lng", precision = 10, scale = 7)
    private java.math.BigDecimal mapLng;

    @Column(name = "map_place", length = 300)
    private String mapPlace;

    @Column(name = "notice_title", length = 200)
    private String noticeTitle;

    @Column(name = "notice_body", columnDefinition = "TEXT")
    private String noticeBody;

    @Column(name = "notice_video_url", length = 500)
    private String noticeVideoUrl;

    @Column(name = "notice_poster_url", length = 500)
    private String noticePosterUrl;

    @Column(name = "notice_published", nullable = false)
    private boolean noticePublished = false;

    @Column(length = 200)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Column(name = "academic_session", length = 50)
    private String academicSession;

    @Column(nullable = false, length = 50)
    private String timezone = "Asia/Karachi";

    @Column(columnDefinition = "TEXT")
    private String notes;

    public boolean allowsSchoolLogin() {
        return status == TenantStatus.ACTIVE || status == TenantStatus.PENDING;
    }
}
