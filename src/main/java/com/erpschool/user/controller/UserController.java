package com.erpschool.user.controller;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.common.dto.PageResponse;
import com.erpschool.user.dto.CreateUserRequest;
import com.erpschool.user.dto.CreateUserResponse;
import com.erpschool.user.dto.UpdateUserRequest;
import com.erpschool.user.dto.UserResponse;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.entity.UserStatus;
import com.erpschool.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    @Operation(summary = "Create a school user (username + temporary password are generated)")
    public ResponseEntity<ApiResponse<CreateUserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User created", userService.create(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    @Operation(summary = "List users in a school. ERP Owner must pass tenantId.")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> list(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(userService.list(tenantId, role, status, q, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN','PRINCIPAL')")
    public ResponseEntity<ApiResponse<UserResponse>> get(@PathVariable UUID id,
                                                         @RequestParam(required = false) UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.get(id, tenantId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> update(@PathVariable UUID id,
                                                            @RequestParam(required = false) UUID tenantId,
                                                            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("User updated", userService.update(id, tenantId, request)));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> activate(@PathVariable UUID id,
                                                              @RequestParam(required = false) UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.ok("User activated", userService.activate(id, tenantId)));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> deactivate(@PathVariable UUID id,
                                                                @RequestParam(required = false) UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.ok("User deactivated", userService.deactivate(id, tenantId)));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyRole('ERP_OWNER','SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<CreateUserResponse>> resetPassword(@PathVariable UUID id,
                                                                         @RequestParam(required = false) UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.ok("Password reset", userService.resetPassword(id, tenantId)));
    }
}
