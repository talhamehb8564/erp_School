package com.erpschool.auth.controller;

import com.erpschool.auth.dto.AuthResponse;
import com.erpschool.auth.dto.ChangePasswordRequest;
import com.erpschool.auth.dto.LoginRequest;
import com.erpschool.auth.dto.LogoutRequest;
import com.erpschool.auth.dto.RefreshRequest;
import com.erpschool.auth.service.AuthService;
import com.erpschool.common.dto.ApiResponse;
import com.erpschool.security.CurrentUser;
import com.erpschool.user.dto.UserResponse;
import com.erpschool.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Login with username and password")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.ok("Login successful",
                authService.login(request.getUsername(), request.getPassword(), http)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Issue a new access token using a refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest request,
                                                             HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request.getRefreshToken(), http)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the given refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok("Logged out", null));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password for the authenticated user")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        User user = authService.requireUser(CurrentUser.require().getId());
        authService.changePassword(user, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password changed. Please login again.", null));
    }

    @GetMapping("/me")
    @Operation(summary = "Current authenticated user")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        User user = authService.requireUser(CurrentUser.require().getId());
        return ResponseEntity.ok(ApiResponse.ok(authService.me(user)));
    }
}
