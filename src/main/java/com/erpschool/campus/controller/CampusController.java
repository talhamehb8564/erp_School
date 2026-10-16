package com.erpschool.campus.controller;

import com.erpschool.campus.entity.Campus;
import com.erpschool.campus.service.CampusService;
import com.erpschool.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campuses")
@Tag(name = "Campuses")
public class CampusController {

    private final CampusService campusService;

    public CampusController(CampusService campusService) {
        this.campusService = campusService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Campus>> create(@Valid @RequestBody CampusRequest request) {
        Campus campus = new Campus();
        campus.setName(request.getName());
        campus.setCode(request.getCode());
        campus.setAddress(request.getAddress());
        campus.setCity(request.getCity());
        campus.setPhone(request.getPhone());
        campus.setEmail(request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Campus created", campusService.create(campus)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL','TEACHER','ACCOUNT_OFFICER')")
    public ResponseEntity<ApiResponse<List<Campus>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(campusService.list()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Campus>> update(@PathVariable UUID id, @RequestBody CampusRequest request) {
        Campus campus = new Campus();
        campus.setName(request.getName());
        campus.setAddress(request.getAddress());
        campus.setCity(request.getCity());
        campus.setPhone(request.getPhone());
        campus.setEmail(request.getEmail());
        campus.setFacebookUrl(request.getFacebookUrl());
        campus.setInstagramUrl(request.getInstagramUrl());
        campus.setYoutubeUrl(request.getYoutubeUrl());
        campus.setTwitterUrl(request.getTwitterUrl());
        if (request.getHero() != null) {
            try {
                campus.setHeroJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request.getHero()));
            } catch (Exception ignored) {
                campus.setHeroJson("[]");
            }
        }
        if (request.getMapLat() != null || request.getMapLng() != null
                || request.getMapPlace() != null || request.getWhatsappUrl() != null) {
            campus.setWebsiteGeoUpdate(true);
            campus.setMapLat(parseDecimal(request.getMapLat()));
            campus.setMapLng(parseDecimal(request.getMapLng()));
            campus.setMapPlace(emptyToNull(request.getMapPlace()));
            campus.setWhatsappUrl(emptyToNull(request.getWhatsappUrl()));
        }
        return ResponseEntity.ok(ApiResponse.ok("Campus updated", campusService.update(id, campus)));
    }

    @Getter
    @Setter
    public static class CampusRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String code;
        private String address;
        private String city;
        private String phone;
        private String email;
        private String facebookUrl;
        private String instagramUrl;
        private String youtubeUrl;
        private String twitterUrl;
        private String whatsappUrl;
        private String mapLat;
        private String mapLng;
        private String mapPlace;
        private java.util.List<java.util.Map<String, Object>> hero;
    }

    private static String emptyToNull(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }

    private static java.math.BigDecimal parseDecimal(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return new java.math.BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
