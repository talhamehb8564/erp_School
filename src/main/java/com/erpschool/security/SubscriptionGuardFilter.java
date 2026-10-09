package com.erpschool.security;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.tenant.context.TenantContext;
import com.erpschool.tenant.entity.Tenant;
import com.erpschool.tenant.entity.TenantStatus;
import com.erpschool.tenant.repository.TenantRepository;
import com.erpschool.user.entity.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * When a school's tenant is suspended, expired or disabled, only billing /
 * identity endpoints remain available (and only for the school administrator).
 * ERP Owner is never blocked. Enforcement is server-side, not frontend-only.
 */
@Component
public class SubscriptionGuardFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE = "SUBSCRIPTION_INACTIVE";
    public static final String MESSAGE =
            "Your school's ERP subscription is currently inactive. Please complete the payment and submit the payment proof to reactivate your account.";

    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    public SubscriptionGuardFilter(TenantRepository tenantRepository, ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        UserRole role = TenantContext.getRole();
        if (role == null || role == UserRole.ERP_OWNER) {
            filterChain.doFilter(request, response);
            return;
        }
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null || tenant.allowsSchoolLogin()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (role == UserRole.SCHOOL_ADMIN && isBillingPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(ERROR_CODE, MESSAGE));
    }

    private boolean isBillingPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path.startsWith("/api/v1/auth/")) {
            return true;
        }
        if ("GET".equals(method) && "/api/v1/tenants/me".equals(path)) {
            return true;
        }
        if ("GET".equals(method) && "/api/v1/subscriptions/current".equals(path)) {
            return true;
        }
        if ("POST".equals(method) && path.matches("/api/v1/subscriptions/[^/]+/payments")) {
            return true;
        }
        if ("POST".equals(method) && "/api/v1/files".equals(path)) {
            return true;
        }
        if ("GET".equals(method) && path.startsWith("/api/v1/files/")) {
            return true;
        }
        if ("GET".equals(method) && "/api/v1/school-settings".equals(path)) {
            return true;
        }
        return false;
    }
}
