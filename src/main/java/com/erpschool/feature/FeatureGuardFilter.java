package com.erpschool.feature;

import com.erpschool.common.dto.ApiResponse;
import com.erpschool.tenant.context.TenantContext;
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
 * Enforces ERP Owner per-school feature switches on APIs. Nav hiding is not enough.
 * Flags are cached in FeatureService (~15s) so this is not a DB round-trip per request.
 */
@Component
public class FeatureGuardFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE = "FEATURE_DISABLED";

    private final FeatureService featureService;
    private final ObjectMapper objectMapper;

    public FeatureGuardFilter(FeatureService featureService, ObjectMapper objectMapper) {
        this.featureService = featureService;
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
        SchoolFeature feature = featureFor(request.getRequestURI());
        if (feature == null || featureService.enabled(tenantId, feature)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.fail(ERROR_CODE, "This module is turned off for your school."));
    }

    static SchoolFeature featureFor(String uri) {
        if (uri == null) {
            return null;
        }
        if (uri.startsWith("/api/v1/attendance/teachers")) {
            return SchoolFeature.TEACHER_ATTENDANCE;
        }
        if (uri.startsWith("/api/v1/attendance")) {
            return SchoolFeature.ATTENDANCE;
        }
        if (uri.startsWith("/api/v1/online-exams")) {
            return SchoolFeature.ONLINE_EXAMS;
        }
        if (uri.startsWith("/api/v1/exams")) {
            return SchoolFeature.RESULTS;
        }
        if (uri.startsWith("/api/v1/fees")) {
            return SchoolFeature.FEES;
        }
        if (uri.startsWith("/api/v1/homework")) {
            return SchoolFeature.HOMEWORK;
        }
        if (uri.startsWith("/api/v1/timetable") || uri.startsWith("/api/v1/teacher/timetable")) {
            return SchoolFeature.TIMETABLE;
        }
        if (uri.startsWith("/api/v1/notifications")) {
            return SchoolFeature.NOTIFICATIONS;
        }
        if (uri.startsWith("/api/v1/announcements")) {
            return SchoolFeature.ANNOUNCEMENTS;
        }
        if (uri.startsWith("/api/v1/admissions")) {
            return SchoolFeature.ADMISSIONS;
        }
        if (uri.startsWith("/api/v1/students")) {
            return SchoolFeature.STUDENTS;
        }
        if (uri.startsWith("/api/v1/salaries")) {
            return SchoolFeature.SALARIES;
        }
        if (uri.startsWith("/api/v1/calendar")) {
            return SchoolFeature.CALENDAR;
        }
        if (uri.startsWith("/api/v1/website")) {
            return SchoolFeature.WEBSITE;
        }
        if (uri.startsWith("/api/v1/portals/parent") || uri.startsWith("/api/v1/parents")) {
            return SchoolFeature.PARENT_PORTAL;
        }
        return null;
    }
}
