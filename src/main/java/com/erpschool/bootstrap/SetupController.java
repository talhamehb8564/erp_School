package com.erpschool.bootstrap;

import com.erpschool.common.dto.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        return ResponseEntity.ok(ApiResponse.ok(setupService.status()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> complete(@RequestBody SetupRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("School created",
                setupService.complete(request.getSchoolName(), request.getBranches())));
    }

    @Getter
    @Setter
    public static class SetupRequest {
        @NotBlank
        private String schoolName;
        private List<String> branches;
    }
}
