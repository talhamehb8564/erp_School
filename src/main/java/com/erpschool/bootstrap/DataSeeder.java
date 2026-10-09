package com.erpschool.bootstrap;

import com.erpschool.common.exception.DuplicateResourceException;
import com.erpschool.config.AppProperties;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.entity.TenantStatus;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.entity.UserStatus;
import com.erpschool.user.repository.UserRepository;
import com.erpschool.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the ERP owner and Green Valley demo school one account per short
 * Spring Data transaction. A single {@code @Transactional} around this runner
 * held the Neon pooler connection through BCrypt and bulk {@code users}
 * flushes, which produced SQLState 08006 and rolled back School Admin.
 */
@Slf4j
@Component
@Order(10)
public class DataSeeder implements ApplicationRunner {

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final SeedRetry seedRetry;

    public DataSeeder(AppProperties properties,
                      UserRepository userRepository,
                      TenantRepository tenantRepository,
                      PasswordEncoder passwordEncoder,
                      UserService userService,
                      SeedRetry seedRetry) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.seedRetry = seedRetry;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getSeed().isEnabled()) {
            return;
        }
        seedRetry.run("erp-owner", this::seedErpOwner);
        Tenant tenant = seedRetry.call("demo-tenant", this::ensureDemoTenant);
        String password = properties.getSeed().getDemoUserPassword();
        seedDemoUser(tenant, UserRole.SCHOOL_ADMIN, "School", "Admin", "admin@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.PRINCIPAL, "Ayesha", "Malik", "principal@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.TEACHER, "Ali", "Raza", "teacher@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.ACCOUNT_OFFICER, "Nadia", "Khan", "accounts@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.PARENT, "Ahmed", "Khan", "parent@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.STUDENT, "Hassan", "Khan", "student@greenvalley.school", password);

        userRepository.findByEmailIgnoreCase("admin@greenvalley.school").ifPresent(admin ->
                log.info("Demo School Admin ready username={} tenant={} campus={}",
                        admin.getUsername(), admin.getTenantId(), admin.getCampusId()));
    }

    private void seedErpOwner() {
        String username = properties.getSeed().getErpOwnerUsername();
        String password = properties.getSeed().getErpOwnerPassword();
        User owner = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (owner == null) {
            owner = userRepository.findByEmailIgnoreCase(properties.getSeed().getErpOwnerEmail()).orElse(null);
        }
        if (owner == null) {
            owner = new User();
            owner.setUsername(username);
            owner.setEmail(properties.getSeed().getErpOwnerEmail());
            owner.setFirstName("ERP");
            owner.setLastName("Owner");
            owner.setRole(UserRole.ERP_OWNER);
            owner.setPasswordHash(passwordEncoder.encode(password));
            owner.setStatus(UserStatus.ACTIVE);
            owner.setMustChangePassword(false);
            userRepository.save(owner);
            log.info("Seeded ERP owner account '{}'", username);
            return;
        }
        boolean dirty = false;
        if (!username.equalsIgnoreCase(owner.getUsername())) {
            owner.setUsername(username);
            dirty = true;
        }
        if (owner.getRole() != UserRole.ERP_OWNER) {
            owner.setRole(UserRole.ERP_OWNER);
            dirty = true;
        }
        dirty = unlock(owner) || dirty;
        if (dirty) {
            userRepository.save(owner);
        }
        log.info("ERP owner '{}' already present", username);
    }

    private Tenant ensureDemoTenant() {
        String code = properties.getSeed().getDemoSchoolCode();
        Tenant tenant = tenantRepository.findByCodeIgnoreCase(code).orElse(null);
        if (tenant == null) {
            Tenant created = new Tenant();
            created.setCode(code);
            created.setName(properties.getSeed().getDemoSchoolName());
            created.setEmail("admin@greenvalley.school");
            created.setPhone("+92-300-0000000");
            created.setAddressLine("Demo Campus, Lahore");
            created.setCity("Lahore");
            created.setCountry("Pakistan");
            created.setAcademicYear("2026-2027");
            created.setAcademicSession("2026-2027");
            created.setStatus(TenantStatus.ACTIVE);
            created.setTimezone("Asia/Karachi");
            tenant = tenantRepository.save(created);
            log.info("Seeded demo tenant {}", code);
        } else if (tenant.getStatus() != TenantStatus.ACTIVE) {
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant = tenantRepository.save(tenant);
        }
        return tenant;
    }

    private void seedDemoUser(Tenant tenant, UserRole role, String first, String last, String email, String password) {
        seedRetry.run("demo-" + role, () -> {
            User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
            if (existing == null) {
                try {
                    userService.createInternal(tenant.getId(), tenant.getCode(), role, first, last, email, null, password, false);
                    log.info("Seeded demo {} for school {}", role, tenant.getCode());
                } catch (DuplicateResourceException ignored) {
                    log.info("Demo {} already existed on retry", role);
                }
                existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
            }
            if (existing == null) {
                throw new IllegalStateException("Failed to persist demo " + role + " (" + email + ")");
            }
            boolean dirty = false;
            if (!first.equals(existing.getFirstName())) {
                existing.setFirstName(first);
                dirty = true;
            }
            if (!last.equals(existing.getLastName())) {
                existing.setLastName(last);
                dirty = true;
            }
            if (existing.getRole() != role) {
                existing.setRole(role);
                dirty = true;
            }
            dirty = restoreCanonicalUsername(existing, tenant.getCode(), role) || dirty;
            dirty = unlock(existing) || dirty;
            if (dirty) {
                userRepository.save(existing);
            }
        });
    }

    private boolean restoreCanonicalUsername(User user, String tenantCode, UserRole role) {
        String canonical = tenantCode.toUpperCase() + "-" + role.getUsernameCode() + "-0001";
        if (canonical.equalsIgnoreCase(user.getUsername())) {
            return false;
        }
        boolean taken = userRepository.findByUsernameIgnoreCase(canonical)
                .filter(other -> !other.getId().equals(user.getId()))
                .isPresent();
        if (taken) {
            return false;
        }
        log.info("Restoring demo username {} -> {}", user.getUsername(), canonical);
        user.setUsername(canonical);
        return true;
    }

    private static boolean unlock(User user) {
        boolean dirty = false;
        if (user.getStatus() != UserStatus.ACTIVE) {
            user.setStatus(UserStatus.ACTIVE);
            dirty = true;
        }
        if (user.getFailedLoginAttempts() != 0) {
            user.setFailedLoginAttempts(0);
            dirty = true;
        }
        if (user.getLockedUntil() != null) {
            user.setLockedUntil(null);
            dirty = true;
        }
        if (user.isMustChangePassword()) {
            user.setMustChangePassword(false);
            dirty = true;
        }
        return dirty;
    }
}
