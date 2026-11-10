package com.erpschool.website;

import com.erpschool.admission.AdmissionService;
import com.erpschool.announcement.entity.Announcement;
import com.erpschool.announcement.repository.AnnouncementRepository;
import com.erpschool.campus.repository.CampusRepository;
import com.erpschool.common.cache.CatalogCache;
import com.erpschool.file.controller.FileController;
import com.erpschool.file.entity.StoredFile;
import com.erpschool.file.repository.StoredFileRepository;
import com.erpschool.file.service.FileStorageService;
import com.erpschool.settings.entity.SchoolSettings;
import com.erpschool.settings.repository.SchoolSettingsRepository;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.entity.TenantStatus;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NoticeAttachmentTest {

    @TempDir
    Path tempUploadDir;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void announcementSupportsSingleImageVideoDocumentAndMultipleAttachmentsWithProductionRelativeUrls() {
        UUID tenantId = UUID.randomUUID();
        Announcement notice = new Announcement();
        notice.setTenantId(tenantId);
        notice.setTitle("Annual Sports & Science Exhibition");
        notice.setBody("Check the attached schedule, poster, and intro video.");
        notice.setAttachments(List.of(
                Map.of(
                        "fileName", "poster.png",
                        "fileUrl", "http://localhost:8080/api/v1/files/" + tenantId + "/poster.png",
                        "contentType", "image/png"
                ),
                Map.of(
                        "fileName", "intro.mp4",
                        "fileUrl", "/api/v1/files/" + tenantId + "/intro.mp4",
                        "contentType", "video/mp4"
                ),
                Map.of(
                        "fileName", "schedule.pdf",
                        "fileUrl", "http://127.0.0.1:8080/api/v1/files/" + tenantId + "/schedule.pdf",
                        "contentType", "application/pdf"
                )
        ));

        List<Map<String, Object>> attachments = notice.getAttachments();
        assertThat(attachments).hasSize(3);
        assertThat(attachments.get(0))
                .containsEntry("fileName", "poster.png")
                .containsEntry("fileUrl", "/api/v1/files/" + tenantId + "/poster.png")
                .containsEntry("contentType", "image/png");
        assertThat(attachments.get(1))
                .containsEntry("fileName", "intro.mp4")
                .containsEntry("fileUrl", "/api/v1/files/" + tenantId + "/intro.mp4")
                .containsEntry("contentType", "video/mp4");
        assertThat(attachments.get(2))
                .containsEntry("fileName", "schedule.pdf")
                .containsEntry("fileUrl", "/api/v1/files/" + tenantId + "/schedule.pdf")
                .containsEntry("contentType", "application/pdf");
        assertThat(notice.getAttachmentUrl()).isEqualTo("/api/v1/files/" + tenantId + "/poster.png");
    }

    @Test
    void websiteNoticePersistsAndExposesMultipleAttachmentsOnPublicSchoolEndpoint() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        TenantContext.setRole(UserRole.SCHOOL_ADMIN);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setCode("GVS");
        tenant.setName("Green Valley School");
        tenant.setStatus(TenantStatus.ACTIVE);

        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.findByCodeIgnoreCase("GVS")).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        SchoolSettingsRepository settingsRepository = mock(SchoolSettingsRepository.class);
        when(settingsRepository.findById(tenantId)).thenReturn(Optional.of(new SchoolSettings()));
        when(settingsRepository.save(any(SchoolSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        CampusRepository campusRepository = mock(CampusRepository.class);
        when(campusRepository.findByTenantIdOrderByNameAsc(tenantId)).thenReturn(List.of());

        AdmissionService admissionService = mock(AdmissionService.class);
        when(admissionService.publicSummary(tenantId)).thenReturn(Map.of("enabled", true));

        AnnouncementRepository announcementRepository = mock(AnnouncementRepository.class);
        Announcement portalNotice = new Announcement();
        portalNotice.setId(UUID.randomUUID());
        portalNotice.setTenantId(tenantId);
        portalNotice.setTitle("Midterm Date Sheet Notice");
        portalNotice.setBody("Download the PDF date sheet below.");
        portalNotice.setAudience("ALL");
        portalNotice.setPublishDate(LocalDate.now());
        portalNotice.setAttachments(List.of(
                Map.of("fileName", "datesheet.pdf", "fileUrl", "/api/v1/files/" + tenantId + "/datesheet.pdf", "contentType", "application/pdf")
        ));
        when(announcementRepository.findByTenantIdOrderByPublishDateDesc(tenantId)).thenReturn(List.of(portalNotice));

        CatalogCache catalogCache = new CatalogCache();
        PublicSchoolService publicSchoolService = new PublicSchoolService(
                tenantRepository, campusRepository, catalogCache, admissionService, announcementRepository
        );
        WebsiteInquiryRepository inquiryRepository = mock(WebsiteInquiryRepository.class);
        WebsiteService websiteService = new WebsiteService(
                tenantRepository, settingsRepository, catalogCache, inquiryRepository, campusRepository, publicSchoolService
        );

        websiteService.save(Map.of(
                "noticeTitle", "Admissions Open 2026",
                "noticeBody", "See attached brochure, banner image, and campus tour video.",
                "noticePublished", true,
                "noticeAttachments", List.of(
                        Map.of("fileName", "banner.jpg", "fileUrl", "http://localhost:8080/api/v1/files/" + tenantId + "/banner.jpg", "contentType", "image/jpeg"),
                        Map.of("fileName", "tour.mp4", "fileUrl", "/api/v1/files/" + tenantId + "/tour.mp4", "contentType", "video/mp4"),
                        Map.of("fileName", "brochure.docx", "fileUrl", "/api/v1/files/" + tenantId + "/brochure.docx", "contentType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                )
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> branding = publicSchoolService.branding("GVS");
        @SuppressWarnings("unchecked")
        Map<String, Object> school = (Map<String, Object>) branding.get("school");
        @SuppressWarnings("unchecked")
        Map<String, Object> notice = (Map<String, Object>) school.get("notice");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attachments = (List<Map<String, Object>>) notice.get("attachments");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> publicAnnouncements = (List<Map<String, Object>>) school.get("announcements");

        assertThat(notice.get("isDefault")).isEqualTo(false);
        assertThat(notice.get("posterUrl")).isEqualTo("/api/v1/files/" + tenantId + "/banner.jpg");
        assertThat(notice.get("videoUrl")).isEqualTo("/api/v1/files/" + tenantId + "/tour.mp4");
        assertThat(attachments).hasSize(3);
        assertThat(publicAnnouncements).hasSize(1);
    }

    @Test
    void fileStorageAndControllerServeFromDatabaseFallbackInProductionAndEnforceTenantIsolation() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        TenantContext.setTenantId(tenantA);

        StoredFileRepository repo = mock(StoredFileRepository.class);
        final StoredFile[] savedHolder = new StoredFile[1];
        when(repo.save(any(StoredFile.class))).thenAnswer(inv -> {
            StoredFile sf = inv.getArgument(0);
            sf.setId(UUID.randomUUID());
            savedHolder[0] = sf;
            return sf;
        });

        FileStorageService storageService = new FileStorageService(repo, tempUploadDir.toString());
        // Valid PNG magic header: 0x89 'P' 'N' 'G' 0x0D 0x0A 0x1A 0x0A
        byte[] pngBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile uploadFile = new MockMultipartFile("file", "notice-image.png", "image/png", pngBytes);

        Map<String, Object> uploadResult = storageService.store(uploadFile);
        String url = String.valueOf(uploadResult.get("url"));
        String storedName = url.substring(url.lastIndexOf('/') + 1);

        assertThat(savedHolder[0]).isNotNull();
        assertThat(savedHolder[0].getFileData()).isEqualTo(pngBytes);

        when(repo.findFirstByTenantIdAndStoredPathOrderByCreatedAtDesc(tenantA, url))
                .thenReturn(Optional.of(savedHolder[0]));
        when(repo.findFirstByTenantIdAndStoredPathOrderByCreatedAtDesc(tenantB, "/api/v1/files/" + tenantB + "/" + storedName))
                .thenReturn(Optional.empty());

        // Simulate ephemeral container restart in production by deleting local disk file
        Path diskFile = storageService.resolvePublic(tenantA, storedName);
        Files.deleteIfExists(diskFile);
        assertThat(Files.exists(diskFile)).isFalse();

        FileController controller = new FileController(storageService);

        // Tenant A request succeeds from DB fallback with proper image/png MIME type and nosniff
        ResponseEntity<Resource> response = controller.download(tenantA, storedName);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContentAsByteArray()).isEqualTo(pngBytes);

        // Tenant B cannot access Tenant A's file (tenant isolation)
        ResponseEntity<Resource> crossTenantResponse = controller.download(tenantB, storedName);
        assertThat(crossTenantResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
