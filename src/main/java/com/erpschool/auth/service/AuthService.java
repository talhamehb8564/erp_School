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
import com.erpschool.tenant.dto.TenantResponse;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 8;
    private static final int LOCK_MINUTES = 15;
    private static final Executor LOGIN_FOLLOW_UP = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "auth-login-followup");
        t.setDaemon(true);
        return t;
    });

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
     * BCrypt runs outside the write transaction so Neon/PgBouncer is not left
     * idle-in-transaction. User+tenant load share one read connection; last login,
     * refresh token, and audit share one write connection (no extra Hikari checkout).
     */
    public AuthResponse login(String username, String password, HttpServletRequest request) {
        String identifier = username == null ? "" : username.trim();
        LoginRow loaded = readTx.execute(status -> {
            User found = userRepository.findByUsernameIgnoreCase(identifier).orElse(null);
            if (found == null && !identifier.isEmpty()) {
                found = userRepository.findByEmailIgnoreCase(identifier).orElse(null);
            }
            if (found == null) {
                return new LoginRow(null, null);
            }
            Tenant school = null;
            if (found.getRole() != UserRole.ERP_OWNER && found.getTenantId() != null) {
                school = tenantRepository.findById(found.getTenantId()).orElse(null);
            }
            return new LoginRow(found, school);
        });
        User user = loaded == null ? null : loaded.user();
        if (user == null) {
            writeTx.executeWithoutResult(status -> auditService.recordSameTransaction(
                    null, null, identifier, null, AuditService.LOGIN_FAILED,
                    "User", null, Map.of("reason", "unknown_user")));
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
            writeTx.executeWithoutResult(status -> {
                registerFailure(user);
                auditService.recordSameTransaction(user.getTenantId(), user.getId(), user.getUsername(),
                        user.getRole().name(), AuditService.LOGIN_FAILED, "User", user.getId().toString(),
                        Map.of("reason", "bad_password"));
            });
            throw new UnauthorizedException("Invalid username or password");
        }
        Tenant tenant = loaded != null ? loaded.tenant() : null;
        if (user.getRole() != UserRole.ERP_OWNER && tenant == null) {
            throw new UnauthorizedException("School account is not available");
        }
        assertTenantAllowsLogin(user, tenant);

        Instant now = Instant.now();
        String ip = HttpRequestUtils.clientIp(request);
        String ua = HttpRequestUtils.userAgent(request);
        UUID userId = user.getId();
        UUID tenantId = user.getTenantId();
        String accountUsername = user.getUsername();
        String role = user.getRole().name();
        /* Tokens first so the HTTP response is not blocked on users.last_login_at
           (the Neon write that previously stalled login for several seconds).
           last_login and LOGIN_SUCCESS audit still run; they just follow the response. */
        AuthResponse response = writeTx.execute(status -> issueTokens(user, request, tenant));
        if (response == null) {
            throw new UnauthorizedException("Unable to complete login");
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        user.setStatus(UserStatus.ACTIVE);
        LOGIN_FOLLOW_UP.execute(() -> {
            try {
                writeTx.executeWithoutResult(status -> {
                    userRepository.markSuccessfulLogin(userId, now, UserStatus.ACTIVE);
                    auditService.recordSameTransaction(tenantId, userId, accountUsername, role,
                            AuditService.LOGIN_SUCCESS, "User", userId.toString(), null, ip, ua);
                });
            } catch (RuntimeException ignored) {
                /* Caller already has a valid session. */
            }
        });
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
        Tenant tenant = loadTenantForLogin(user);
        assertTenantAllowsLogin(user, tenant);
        stored.revoke();
        refreshTokenRepository.save(stored);
        return issueTokens(user, request, tenant);
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

    public UserResponse changePassword(User actor, String currentPassword, String newPassword) {
        return changeCredentials(actor, currentPassword, newPassword, null);
    }

    /**
     * Verify the current password and encode any new hash outside the write
     * transaction so Neon is not left idle-in-transaction during BCrypt.
     * Username-only, password-only, or both are persisted with saveAndFlush.
     */
    public UserResponse changeCredentials(User actor, String currentPassword, String newPassword, String newUsername) {
        User loaded = userRepository.findById(actor.getId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (!passwordEncoder.matches(currentPassword, loaded.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        String trimmedUsername = newUsername == null ? null : newUsername.trim();
        boolean usernameChange = trimmedUsername != null && !trimmedUsername.isEmpty()
                && !trimmedUsername.equalsIgnoreCase(loaded.getUsername());
        boolean passwordChange = newPassword != null && !newPassword.isBlank();
        if (!usernameChange && !passwordChange) {
            throw new BusinessException("Provide a new username and/or a new password");
        }
        if (usernameChange) {
            if (trimmedUsername.length() < 3 || trimmedUsername.length() > 80) {
                throw new BusinessException("Username must be between 3 and 80 characters");
            }
        }
        if (passwordChange) {
            if (currentPassword.equals(newPassword)) {
                throw new BusinessException("NEW_PASSWORD_SAME", "New password must be different from the current password");
            }
            if (newPassword.length() < 8 || newPassword.length() > 72
                    || !newPassword.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$")) {
                throw new BusinessException("Password must be 8–72 characters with upper, lower, digit and a special character");
            }
        }
        String encoded = passwordChange ? passwordEncoder.encode(newPassword) : null;
        UserResponse updated = writeTx.execute(status -> {
            User user = userRepository.findById(actor.getId())
                    .orElseThrow(() -> new UnauthorizedException("User not found"));
            if (usernameChange) {
                UUID currentUserId = user.getId();
                userRepository.findByUsernameIgnoreCase(trimmedUsername)
                        .filter(existing -> !existing.getId().equals(currentUserId))
                        .ifPresent(existing -> {
                            throw new BusinessException("Username is already in use");
                        });
                user.setUsername(trimmedUsername);
            }
            if (passwordChange) {
                user.setPasswordHash(encoded);
                user.setMustChangePassword(false);
                user.setPasswordChangedAt(Instant.now());
            }
            refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
            return UserResponse.from(userRepository.saveAndFlush(user));
        });
        if (updated == null) {
            throw new BusinessException("Unable to save credentials");
        }
        auditService.record(AuditService.PASSWORD_CHANGED, "User", actor.getId().toString(),
                Map.of("usernameChanged", usernameChange, "passwordChanged", passwordChange));
        return updated;
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

    private AuthResponse issueTokens(User user, HttpServletRequest request, Tenant tenant) {
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
                .tenant(tenant == null ? null : TenantResponse.from(tenant))
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

    private Tenant loadTenantForLogin(User user) {
        if (user.getRole() == UserRole.ERP_OWNER) {
            return null;
        }
        return tenantRepository.findById(user.getTenantId())
                .orElseThrow(() -> new UnauthorizedException("School account is not available"));
    }

    private void assertTenantAllowsLogin(User user, Tenant tenant) {
        if (user.getRole() == UserRole.ERP_OWNER) {
            return;
        }
        if (tenant == null) {
            throw new UnauthorizedException("School account is not available");
        }
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

    private record LoginRow(User user, Tenant tenant) {
    }
}
