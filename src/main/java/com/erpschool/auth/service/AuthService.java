package com.erpschool.auth.service;

import com.erpschool.audit.service.AuditService;
import com.erpschool.auth.dto.AuthResponse;
import com.erpschool.auth.entity.RefreshToken;
import com.erpschool.auth.repository.RefreshTokenRepository;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.UnauthorizedException;
import com.erpschool.common.util.HttpRequestUtils;
import com.erpschool.common.util.TokenHasher;
import com.erpschool.config.AppProperties;
import com.erpschool.security.JwtService;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.dto.UserResponse;
import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.entity.UserStatus;
import com.erpschool.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 8;
    private static final int LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       TenantRepository tenantRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AppProperties appProperties,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appProperties = appProperties;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse login(String username, String password, HttpServletRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(username.trim()).orElse(null);
        if (user == null) {
            auditService.record(null, null, username, null, AuditService.LOGIN_FAILED,
                    "User", null, Map.of("reason", "unknown_user"));
            throw new UnauthorizedException("Invalid username or password");
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isBefore(Instant.now())) {
                user.setStatus(UserStatus.ACTIVE);
                user.setLockedUntil(null);
                user.setFailedLoginAttempts(0);
            } else {
                throw new UnauthorizedException("Account is locked. Try again later or contact administration.");
            }
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Account is inactive");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            registerFailure(user);
            auditService.record(user.getTenantId(), user.getId(), user.getUsername(), user.getRole().name(),
                    AuditService.LOGIN_FAILED, "User", user.getId().toString(), Map.of("reason", "bad_password"));
            throw new UnauthorizedException("Invalid username or password");
        }
        assertTenantAllowsLogin(user);

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        auditService.record(user.getTenantId(), user.getId(), user.getUsername(), user.getRole().name(),
                AuditService.LOGIN_SUCCESS, "User", user.getId().toString(), null);

        return issueTokens(user, request);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenValue, HttpServletRequest request) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(TokenHasher.sha256(refreshTokenValue))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!stored.isUsable()) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!user.isActive()) {
            stored.revoke();
            refreshTokenRepository.save(stored);
            throw new UnauthorizedException("Account is not active");
        }
        assertTenantAllowsLogin(user);
        stored.revoke();
        refreshTokenRepository.save(stored);
        return issueTokens(user, request);
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256(refreshTokenValue))
                .ifPresent(token -> {
                    token.revoke();
                    refreshTokenRepository.save(token);
                });
        auditService.record(AuditService.LOGOUT, "User", null, null);
    }

    @Transactional
    public void changePassword(User actor, String currentPassword, String newPassword) {
        User user = userRepository.findById(actor.getId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException("NEW_PASSWORD_SAME", "New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        auditService.record(AuditService.PASSWORD_CHANGED, "User", user.getId().toString(), null);
    }

    @Transactional(readOnly = true)
    public UserResponse me(User actor) {
        User user = userRepository.findById(actor.getId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        return UserResponse.from(user);
    }

    public User requireUser(java.util.UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
    }

    private AuthResponse issueTokens(User user, HttpServletRequest request) {
        String accessToken = jwtService.createAccessToken(user);
        String refreshToken = TokenHasher.randomToken();

        RefreshToken entity = new RefreshToken();
        entity.setUserId(user.getId());
        entity.setTenantId(user.getTenantId());
        entity.setTokenHash(TokenHasher.sha256(refreshToken));
        entity.setExpiresAt(Instant.now().plusSeconds(appProperties.getJwt().getRefreshTokenTtlSeconds()));
        entity.setUserAgent(HttpRequestUtils.userAgent(request));
        entity.setIpAddress(HttpRequestUtils.clientIp(request));
        refreshTokenRepository.save(entity);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTtlSeconds())
                .user(UserResponse.from(user))
                .build();
    }

    private void registerFailure(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(Instant.now().plus(LOCK_MINUTES, ChronoUnit.MINUTES));
        }
        userRepository.save(user);
    }

    private void assertTenantAllowsLogin(User user) {
        if (user.getRole() == UserRole.ERP_OWNER) {
            return;
        }
        Tenant tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(() -> new UnauthorizedException("School account is not available"));
        if (!tenant.allowsSchoolLogin()) {
            throw new UnauthorizedException("School access is " + tenant.getStatus().name().toLowerCase());
        }
    }
}
