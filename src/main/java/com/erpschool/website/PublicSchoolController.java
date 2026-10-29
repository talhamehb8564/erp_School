package com.erpschool.website;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public school website")
public class PublicSchoolController {

    private static final long CONTACT_COOLDOWN_MS = 20_000;
    private final ConcurrentHashMap<String, Long> contactHits = new ConcurrentHashMap<>();

    private final PublicSchoolService publicSchoolService;
    private final WebsiteService websiteService;

    public PublicSchoolController(PublicSchoolService publicSchoolService, WebsiteService websiteService) {
        this.publicSchoolService = publicSchoolService;
        this.websiteService = websiteService;
    }

    @GetMapping("/school")
    public ResponseEntity<ApiResponse<Map<String, Object>>> school(
            @RequestParam(required = false) String code) {
        return ResponseEntity.ok(ApiResponse.ok(publicSchoolService.branding(code)));
    }

    @GetMapping("/school/branding")
    public ResponseEntity<ApiResponse<Map<String, Object>>> branding(
            @RequestParam(required = false) String code) {
        return ResponseEntity.ok(ApiResponse.ok(publicSchoolService.branding(code)));
    }

    @PostMapping("/contact")
    public ResponseEntity<ApiResponse<Map<String, Object>>> contact(
            @RequestParam(required = false) String code,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String ip = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        long now = System.currentTimeMillis();
        Long last = contactHits.put(ip, now);
        if (last != null && now - last < CONTACT_COOLDOWN_MS) {
            throw new BusinessException("Please wait a moment before sending another message");
        }
        if (contactHits.size() > 4000) {
            contactHits.entrySet().removeIf(e -> now - e.getValue() > 60_000);
        }
        return ResponseEntity.ok(ApiResponse.ok(
                "Message sent",
                websiteService.submitInquiry(publicSchoolService.resolvePublic(code), body)));
    }
}
