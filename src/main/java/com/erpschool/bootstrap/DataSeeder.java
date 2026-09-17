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
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            return;
        }
        User owner = new User();
        owner.setUsername(username);
        owner.setEmail(properties.getSeed().getErpOwnerEmail());
        owner.setPasswordHash(passwordEncoder.encode(properties.getSeed().getErpOwnerPassword()));
        owner.setFirstName("ERP");
        owner.setLastName("Owner");
        owner.setRole(UserRole.ERP_OWNER);
        owner.setStatus(UserStatus.ACTIVE);
        owner.setMustChangePassword(false);
        userRepository.save(owner);
        log.info("Seeded ERP owner account '{}'", username);
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

        String password = properties.getSeed().getDemoUserPassword();
        seedDemoUser(tenant, UserRole.SCHOOL_ADMIN, "School", "Admin", "admin@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.PRINCIPAL, "Ayesha", "Malik", "principal@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.TEACHER, "Ali", "Raza", "teacher@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.ACCOUNT_OFFICER, "Nadia", "Khan", "accounts@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.PARENT, "Ahmed", "Khan", "parent@greenvalley.school", password);
        seedDemoUser(tenant, UserRole.STUDENT, "Hassan", "Khan", "student@greenvalley.school", password);
    }

    private void seedDemoUser(Tenant tenant, UserRole role, String first, String last, String email, String password) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        userService.createInternal(tenant.getId(), tenant.getCode(), role, first, last, email, null, password, true);
        log.info("Seeded demo {} for school {}", role, tenant.getCode());
    }
}
