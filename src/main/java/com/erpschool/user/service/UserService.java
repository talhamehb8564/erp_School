package com.erpschool.user.service;

import com.erpschool.audit.service.AuditService;
import com.erpschool.common.dto.PageResponse;
import com.erpschool.common.exception.BusinessException;
import com.erpschool.common.exception.DuplicateResourceException;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.PasswordGenerator;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.dto.CreateUserRequest;
import com.erpschool.user.dto.CreateUserResponse;
import com.erpschool.user.dto.UpdateUserRequest;
import com.erpschool.user.dto.UserResponse;
import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.entity.UserStatus;
import com.erpschool.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final UsernameGenerator usernameGenerator;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       TenantRepository tenantRepository,
                       UsernameGenerator usernameGenerator,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.usernameGenerator = usernameGenerator;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public CreateUserResponse create(CreateUserRequest request) {
        if (request.getRole() == UserRole.ERP_OWNER) {
            throw new ForbiddenException("Cannot create another ERP owner through this API");
        }
        UUID tenantId = TenantGuard.requireTenantId(request.getTenantId());
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        if (request.getEmail() != null && userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new DuplicateResourceException("Email is already in use");
        }

        String username = usernameGenerator.next(tenant.getId(), tenant.getCode(), request.getRole());
        String temporaryPassword = PasswordGenerator.temporaryPassword();

        User user = new User();
        user.setTenantId(tenant.getId());
        user.setUsername(username);
        user.setEmail(blankToNull(request.getEmail()));
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setPhone(blankToNull(request.getPhone()));
        user.setRole(request.getRole());
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(true);
        user.setCreatedBy(TenantContext.getUserId());
        user = userRepository.save(user);

        auditService.record(AuditService.USER_CREATED, "User", user.getId().toString(),
                Map.of("username", user.getUsername(), "role", user.getRole().name()));

        return CreateUserResponse.builder()
                .user(UserResponse.from(user))
                .temporaryPassword(temporaryPassword)
                .message("Account created. Share the temporary password securely. The user must change it after login.")
                .build();
    }

    /**
     * Used by tenant creation / seeder where temporary password may be chosen.
     */
    @Transactional
    public CreateUserResponse createInternal(UUID tenantId,
                                             String tenantCode,
                                             UserRole role,
                                             String firstName,
                                             String lastName,
                                             String email,
                                             String phone,
                                             String rawPassword,
                                             boolean mustChangePassword) {
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email is already in use");
        }
        String username = usernameGenerator.next(tenantId, tenantCode, role);
        String password = rawPassword != null ? rawPassword : PasswordGenerator.temporaryPassword();
        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setEmail(blankToNull(email));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setPhone(blankToNull(phone));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(mustChangePassword);
        user.setCreatedBy(TenantContext.getUserId());
        user = userRepository.save(user);
        return CreateUserResponse.builder()
                .user(UserResponse.from(user))
                .temporaryPassword(password)
                .message("Account created")
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(UUID requestedTenantId,
                                           UserRole role,
                                           UserStatus status,
                                           String q,
                                           Pageable pageable) {
        UUID tenantId = TenantGuard.requireTenantId(requestedTenantId);
        Page<UserResponse> page = userRepository
                .searchByTenant(tenantId, role, status, blankToNull(q), pageable)
                .map(UserResponse::from);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id, UUID requestedTenantId) {
        User user = findScoped(id, requestedTenantId);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse update(UUID id, UUID requestedTenantId, UpdateUserRequest request) {
        User user = findScoped(id, requestedTenantId);
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(blankToNull(request.getPhone()));
        }
        if (request.getEmail() != null) {
            String email = blankToNull(request.getEmail());
            if (email != null) {
                userRepository.findByEmailIgnoreCase(email)
                        .filter(existing -> !existing.getId().equals(user.getId()))
                        .ifPresent(existing -> {
                            throw new DuplicateResourceException("Email is already in use");
                        });
            }
            user.setEmail(email);
        }
        user.setUpdatedBy(TenantContext.getUserId());
        user = userRepository.save(user);
        auditService.record(AuditService.USER_UPDATED, "User", user.getId().toString(),
                Map.of("username", user.getUsername()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse activate(UUID id, UUID requestedTenantId) {
        User user = findScoped(id, requestedTenantId);
        if (user.getRole() == UserRole.ERP_OWNER) {
            throw new ForbiddenException("Cannot change ERP owner status");
        }
        user.setStatus(UserStatus.ACTIVE);
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        user.setUpdatedBy(TenantContext.getUserId());
        user = userRepository.save(user);
        auditService.record(AuditService.USER_ACTIVATED, "User", user.getId().toString(),
                Map.of("username", user.getUsername()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse deactivate(UUID id, UUID requestedTenantId) {
        User user = findScoped(id, requestedTenantId);
        if (user.getRole() == UserRole.ERP_OWNER) {
            throw new ForbiddenException("Cannot deactivate the ERP owner");
        }
        if (user.getId().equals(TenantContext.getUserId())) {
            throw new BusinessException("Cannot deactivate your own account");
        }
        user.setStatus(UserStatus.INACTIVE);
        user.setUpdatedBy(TenantContext.getUserId());
        user = userRepository.save(user);
        auditService.record(AuditService.USER_DEACTIVATED, "User", user.getId().toString(),
                Map.of("username", user.getUsername()));
        return UserResponse.from(user);
    }

    @Transactional
    public CreateUserResponse resetPassword(UUID id, UUID requestedTenantId) {
        User user = findScoped(id, requestedTenantId);
        if (user.getRole() == UserRole.ERP_OWNER && !TenantContext.isErpOwner()) {
            throw new ForbiddenException("Cannot reset ERP owner password");
        }
        String temporaryPassword = PasswordGenerator.temporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(Instant.now());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setUpdatedBy(TenantContext.getUserId());
        userRepository.save(user);
        auditService.record(AuditService.PASSWORD_RESET, "User", user.getId().toString(),
                Map.of("username", user.getUsername()));
        return CreateUserResponse.builder()
                .user(UserResponse.from(user))
                .temporaryPassword(temporaryPassword)
                .message("Password reset. Share the temporary password securely.")
                .build();
    }

    public User findScoped(UUID id, UUID requestedTenantId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (user.getRole() == UserRole.ERP_OWNER) {
            if (!TenantContext.isErpOwner() && !user.getId().equals(TenantContext.getUserId())) {
                throw new ResourceNotFoundException("User", id);
            }
            return user;
        }
        UUID tenantId = TenantGuard.requireTenantId(requestedTenantId);
        if (!tenantId.equals(user.getTenantId())) {
            throw new ResourceNotFoundException("User", id);
        }
        return user;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
