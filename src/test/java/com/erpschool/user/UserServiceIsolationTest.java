package com.erpschool.user;

import com.erpschool.audit.service.AuditService;
import com.erpschool.common.exception.ForbiddenException;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.dto.CreateUserRequest;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.repository.UserRepository;
import com.erpschool.user.service.UserService;
import com.erpschool.user.service.UsernameGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class UserServiceIsolationTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UsernameGenerator usernameGenerator;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, tenantRepository, usernameGenerator,
                passwordEncoder, auditService);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void cannotCreateErpOwnerViaApi() {
        TenantContext.set(UUID.randomUUID(), UUID.randomUUID(), "GVS-ADM-0001", UserRole.SCHOOL_ADMIN);
        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName("Plat");
        request.setLastName("Form");
        request.setRole(UserRole.ERP_OWNER);
        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void schoolAdminCannotCreateUserInAnotherSchool() {
        UUID mySchool = UUID.randomUUID();
        UUID otherSchool = UUID.randomUUID();
        TenantContext.set(mySchool, UUID.randomUUID(), "GVS-ADM-0001", UserRole.SCHOOL_ADMIN);
        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName("Ali");
        request.setLastName("Raza");
        request.setRole(UserRole.TEACHER);
        request.setTenantId(otherSchool);
        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ForbiddenException.class);
    }
}
