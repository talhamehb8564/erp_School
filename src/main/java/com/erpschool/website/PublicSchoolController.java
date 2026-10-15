package com.erpschool.website;

import com.erpschool.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public school website")
public class PublicSchoolController {

    private final PublicSchoolService publicSchoolService;

    public PublicSchoolController(PublicSchoolService publicSchoolService) {
        this.publicSchoolService = publicSchoolService;
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
}
