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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

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
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    public AuthService(UserRepository userRepository,
                       TenantRepository tenantRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AppProperties appProperties,
                       AuditService auditService,
                       PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appProperties = appProperties;
        this.auditService = auditService;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    /**
     * BCrypt and audit run outside the write transaction so Neon/PgBouncer is not
     * left idle-in-transaction, and REQUIRES_NEW audit does not need a second
     * pooled connection while {@code users} is still locked.
     */
    public AuthResponse login(String username, String password, HttpServletRequest request) {
        String identifier = username == null ? "" : username.trim();
        User user = readTx.execute(status -> {
            User found = userRepository.findByUsernameIgnoreCase(identifier).orElse(null);
            if (found == null && !identifier.isEmpty()) {
                found = userRepository.findByEmailIgnoreCase(identifier).orElse(null);
            }
            return found;
        });
        if (user == null) {
            auditService.record(null, null, identifier, null, AuditService.LOGIN_FAILED,
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
            writeTx.executeWithoutResult(status -> registerFailure(user));
            auditService.record(user.getTenantId(), user.getId(), user.getUsername(), user.getRole().name(),
                    AuditService.LOGIN_FAILED, "User", user.getId().toString(), Map.of("reason", "bad_password"));
            throw new UnauthorizedException("Invalid username or password");
        }
        assertTenantAllowsLogin(user);

        AuthResponse response = writeTx.execute(status -> {
            User persistent = userRepository.findById(user.getId())
                    .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));
            persistent.setFailedLoginAttempts(0);
            persistent.setLockedUntil(null);
            persistent.setLastLoginAt(Instant.now());
            userRepository.saveAndFlush(persistent);
            return issueTokens(persistent, request);
        });

        auditService.record(user.getTenantId(), user.getId(), user.getUsername(), user.getRole().name(),
                AuditService.LOGIN_SUCCESS, "User", user.getId().toString(), null);

        if (response == null) {
            throw new UnauthorizedException("Unable to complete login");
        }
        return response;
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
        changeCredentials(actor, currentPassword, newPassword, null);
    }

    @Transactional
    public void changeCredentials(User actor, String currentPassword, String newPassword, String newUsername) {
        User user = userRepository.findById(actor.getId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        if (newUsername != null && !newUsername.isBlank()) {
            String username = newUsername.trim();
            UUID currentUserId = user.getId();
            userRepository.findByUsernameIgnoreCase(username)
                    .filter(existing -> !existing.getId().equals(currentUserId))
                    .ifPresent(existing -> {
                        throw new BusinessException("Username is already in use");
                    });
            user.setUsername(username);
        }
        if (newPassword != null && !newPassword.isBlank()) {
            if (currentPassword.equals(newPassword)) {
                throw new BusinessException("NEW_PASSWORD_SAME", "New password must be different from the current password");
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            user.setMustChangePassword(false);
            user.setPasswordChangedAt(Instant.now());
            refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        }
        userRepository.save(user);
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
        User persistent = userRepository.findById(user.getId()).orElse(user);
        int attempts = persistent.getFailedLoginAttempts() + 1;
        persistent.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            persistent.setStatus(UserStatus.LOCKED);
            persistent.setLockedUntil(Instant.now().plus(LOCK_MINUTES, ChronoUnit.MINUTES));
        }
        userRepository.saveAndFlush(persistent);
        user.setFailedLoginAttempts(persistent.getFailedLoginAttempts());
        user.setStatus(persistent.getStatus());
        user.setLockedUntil(persistent.getLockedUntil());
    }

    private void assertTenantAllowsLogin(User user) {
        if (user.getRole() == UserRole.ERP_OWNER) {
            return;
        }
        Tenant tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(() -> new UnauthorizedException("School account is not available"));
        if (tenant.allowsSchoolLogin()) {
            return;
        }
        /* School admin may authenticate while the tenant is locked so they can
         * submit subscription payment proof. Module APIs stay blocked by
         * SubscriptionGuardFilter. Other school roles cannot log in. */
        if (user.getRole() == UserRole.SCHOOL_ADMIN) {
            return;
        }
        throw new UnauthorizedException(
                "Your school's ERP subscription is currently inactive. Please complete the payment and submit the payment proof to reactivate your account.");
    }
}
