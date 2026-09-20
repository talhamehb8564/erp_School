package com.erpschool.auth;

import com.erpschool.audit.service.AuditService;
import com.erpschool.auth.dto.AuthResponse;
import com.erpschool.auth.repository.RefreshTokenRepository;
import com.erpschool.auth.service.AuthService;
import com.erpschool.common.exception.UnauthorizedException;
import com.erpschool.config.AppProperties;
import com.erpschool.security.JwtService;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.entity.TenantStatus;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import com.erpschool.user.entity.UserStatus;
import com.erpschool.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuditService auditService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;
    private UUID tenantId;
    private User teacher;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret("unit-test-jwt-secret-key-32bytes-min");
        JwtService jwtService = new JwtService(properties);
        authService = new AuthService(userRepository, tenantRepository, refreshTokenRepository,
                passwordEncoder, jwtService, properties, auditService);

        tenantId = UUID.randomUUID();
        teacher = new User();
        teacher.setId(UUID.randomUUID());
        teacher.setTenantId(tenantId);
        teacher.setUsername("GVS-TCH-0001");
        teacher.setPasswordHash(passwordEncoder.encode("ChangeMe@123"));
        teacher.setFirstName("Ali");
        teacher.setLastName("Raza");
        teacher.setRole(UserRole.TEACHER);
        teacher.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void loginSucceedsForActiveSchoolUser() {
        when(userRepository.findByUsernameIgnoreCase("GVS-TCH-0001")).thenReturn(Optional.of(teacher));
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login("GVS-TCH-0001", "ChangeMe@123", null);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getUser().getRole()).isEqualTo(UserRole.TEACHER);
        assertThat(response.getUser().getTenantId()).isEqualTo(tenantId);
        verify(refreshTokenRepository).save(ArgumentMatchers.any());
    }

    @Test
    void loginRejectedWhenSchoolSuspended() {
        when(userRepository.findByUsernameIgnoreCase("GVS-TCH-0001")).thenReturn(Optional.of(teacher));
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.SUSPENDED);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> authService.login("GVS-TCH-0001", "ChangeMe@123", null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("subscription is currently inactive");
    }

    @Test
    void schoolAdminCanLoginWhenSchoolSuspendedToSubmitPaymentProof() {
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setTenantId(tenantId);
        admin.setUsername("GVS-ADM-0001");
        admin.setPasswordHash(passwordEncoder.encode("ChangeMe@123"));
        admin.setFirstName("School");
        admin.setLastName("Admin");
        admin.setRole(UserRole.SCHOOL_ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByUsernameIgnoreCase("GVS-ADM-0001")).thenReturn(Optional.of(admin));
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.SUSPENDED);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login("GVS-ADM-0001", "ChangeMe@123", null);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getUser().getRole()).isEqualTo(UserRole.SCHOOL_ADMIN);
    }

    @Test
    void loginSucceedsWithEmailAsWellAsUsername() {
        teacher.setEmail("teacher@greenvalley.school");
        when(userRepository.findByUsernameIgnoreCase("teacher@greenvalley.school")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("teacher@greenvalley.school")).thenReturn(Optional.of(teacher));
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login("teacher@greenvalley.school", "ChangeMe@123", null);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getUser().getUsername()).isEqualTo("GVS-TCH-0001");
    }

    @Test
    void loginRejectedForBadPassword() {
        when(userRepository.findByUsernameIgnoreCase("GVS-TCH-0001")).thenReturn(Optional.of(teacher));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> authService.login("GVS-TCH-0001", "wrong", null))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(teacher.getFailedLoginAttempts()).isEqualTo(1);
    }
}
