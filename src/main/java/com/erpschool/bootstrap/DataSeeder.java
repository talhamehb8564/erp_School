package com.erpschool.bootstrap;

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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Seeds the ERP owner and Green Valley demo school.
 * Existing demo rows are realigned on each boot while seeding is enabled:
 * BCrypt hashes are rewritten with {@link PasswordEncoder} (same bean login uses),
 * accounts are unlocked, and canonical GVS-*-0001 usernames are restored when free.
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

    public DataSeeder(AppProperties properties,
                      UserRepository userRepository,
                      TenantRepository tenantRepository,
                      PasswordEncoder passwordEncoder,
                      UserService userService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.getSeed().isEnabled()) {
            return;
        }
        seedErpOwner();
        seedDemoSchool();
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
        owner.setUsername(username);
        owner.setEmail(properties.getSeed().getErpOwnerEmail());
        owner.setRole(UserRole.ERP_OWNER);
        alignLogin(owner, password);
        userRepository.save(owner);
        log.info("Aligned ERP owner demo login for '{}'", username);
    }

    private void seedDemoSchool() {
        String code = properties.getSeed().getDemoSchoolCode();
        Tenant tenant = tenantRepository.findByCodeIgnoreCase(code).orElseGet(() -> {
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
            return tenantRepository.save(created);
        });
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            tenant.setStatus(TenantStatus.ACTIVE);
            tenantRepository.save(tenant);
        }

        String password = properties.getSeed().getDemoUserPassword();
        seedDemoUser(tenant, UserRole.SCHOOL_ADMIN, "School", "Admin", "admin@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.PRINCIPAL, "Ayesha", "Malik", "principal@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.TEACHER, "Ali", "Raza", "teacher@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.ACCOUNT_OFFICER, "Nadia", "Khan", "accounts@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.PARENT, "Ahmed", "Khan", "parent@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.STUDENT, "Hassan", "Khan", "student@greenvalley.school", password);

        int reset = 0;
        for (User user : userRepository.findByTenantId(tenant.getId())) {
            alignLogin(user, password);
            userRepository.save(user);
            reset++;
        }
        log.info("Aligned {} demo school login(s) for {} to the application PasswordEncoder", reset, tenant.getCode());
    }

    private void seedDemoUser(Tenant tenant, UserRole role, String first, String last, String email, String password) {
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing == null) {
            userService.createInternal(tenant.getId(), tenant.getCode(), role, first, last, email, null, password, false);
            existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
            log.info("Seeded demo {} for school {}", role, tenant.getCode());
        }
        if (existing == null) {
            return;
        }
        existing.setFirstName(first);
        existing.setLastName(last);
        existing.setRole(role);
        existing.setEmail(email);
        restoreCanonicalUsername(existing, tenant.getCode(), role);
        alignLogin(existing, password);
        userRepository.save(existing);
    }

    private void restoreCanonicalUsername(User user, String tenantCode, UserRole role) {
        String canonical = tenantCode.toUpperCase() + "-" + role.getUsernameCode() + "-0001";
        if (canonical.equalsIgnoreCase(user.getUsername())) {
            return;
        }
        boolean taken = userRepository.findByUsernameIgnoreCase(canonical)
                .filter(other -> !other.getId().equals(user.getId()))
                .isPresent();
        if (!taken) {
            log.info("Restoring demo username {} -> {}", user.getUsername(), canonical);
            user.setUsername(canonical);
        }
    }

    private void alignLogin(User user, String rawPassword) {
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setStatus(UserStatus.ACTIVE);
    }
}
