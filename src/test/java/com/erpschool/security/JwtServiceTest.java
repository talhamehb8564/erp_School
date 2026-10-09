package com.erpschool.security;

import com.erpschool.config.AppProperties;
import com.erpschool.user.entity.User;
import com.erpschool.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret("unit-test-jwt-secret-key-32bytes-min");
        properties.getJwt().setAccessTokenTtlSeconds(900);
        jwtService = new JwtService(properties);
    }

    @Test
    void accessTokenContainsRoleAndTenant() {
        User user = new User();
        user.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        user.setTenantId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        user.setUsername("GVS-TCH-0001");
        user.setRole(UserRole.TEACHER);

        String token = jwtService.createAccessToken(user);
        assertThat(jwtService.isValidAccessToken(token)).isTrue();

        Claims claims = jwtService.parse(token);
        assertThat(jwtService.userId(claims)).isEqualTo(user.getId());
        assertThat(jwtService.tenantId(claims)).isEqualTo(user.getTenantId());
        assertThat(claims.get(JwtService.CLAIM_ROLE, String.class)).isEqualTo("TEACHER");
        assertThat(claims.get(JwtService.CLAIM_USERNAME, String.class)).isEqualTo("GVS-TCH-0001");
    }

    @Test
    void erpOwnerTokenHasNoTenant() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("erp.owner");
        user.setRole(UserRole.ERP_OWNER);

        Claims claims = jwtService.parse(jwtService.createAccessToken(user));
        assertThat(jwtService.tenantId(claims)).isNull();
        assertThat(claims.get(JwtService.CLAIM_ROLE, String.class)).isEqualTo("ERP_OWNER");
    }
}
