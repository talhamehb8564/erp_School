package com.erpschool.announcement.controller;

import com.erpschool.announcement.entity.Announcement;
import com.erpschool.announcement.service.AnnouncementService;
import com.erpschool.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
@Tag(name = "Announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<Announcement>> create(@RequestBody Announcement body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Announcement published", announcementService.create(body)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Announcement>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(announcementService.list()));
    }
}
