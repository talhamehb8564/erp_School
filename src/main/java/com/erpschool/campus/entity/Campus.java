package com.erpschool.campus.entity;

import com.erpschool.common.entity.TenantAwareEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "campuses")
public class Campus extends TenantAwareEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(length = 300)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(name = "facebook_url", length = 300)
    private String facebookUrl;

    @Column(name = "instagram_url", length = 300)
    private String instagramUrl;

    @Column(name = "youtube_url", length = 300)
    private String youtubeUrl;

    @Column(name = "twitter_url", length = 300)
    private String twitterUrl;

    @Column(name = "hero_json", columnDefinition = "TEXT")
    private String heroJson;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "primary_color", length = 16)
    private String primaryColor;

    @Column(name = "accent_color", length = 16)
    private String accentColor;

    @JsonIgnore
    @Column(name = "principal_name", length = 200)
    private String principalName;

    @JsonIgnore
    @Column(name = "principal_title", length = 120)
    private String principalTitle;

    @JsonIgnore
    @Column(name = "principal_photo_url", length = 500)
    private String principalPhotoUrl;

    @JsonIgnore
    @Column(name = "principal_message", columnDefinition = "TEXT")
    private String principalMessage;

    @JsonIgnore
    @Column(name = "principal_published", nullable = false)
    private boolean principalPublished = true;

    @JsonIgnore
    @Column(name = "principal_photos_json", columnDefinition = "TEXT")
    private String principalPhotosJson;

    @Column(name = "map_lat", precision = 10, scale = 7)
    private java.math.BigDecimal mapLat;

    @Column(name = "map_lng", precision = 10, scale = 7)
    private java.math.BigDecimal mapLng;

    @Column(name = "map_place", length = 300)
    private String mapPlace;

    @Column(name = "whatsapp_url", length = 300)
    private String whatsappUrl;

    @Transient
    @JsonIgnore
    private boolean websiteGeoUpdate;
}
