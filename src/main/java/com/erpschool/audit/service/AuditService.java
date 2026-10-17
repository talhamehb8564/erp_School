package com.erpschool.audit.service;

import com.erpschool.audit.entity.AuditLog;
import com.erpschool.audit.repository.AuditLogRepository;
import com.erpschool.common.util.HttpRequestUtils;
import com.erpschool.tenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_ACTIVATED = "USER_ACTIVATED";
    public static final String USER_DEACTIVATED = "USER_DEACTIVATED";
    public static final String TENANT_CREATED = "TENANT_CREATED";
    public static final String TENANT_UPDATED = "TENANT_UPDATED";
    public static final String TENANT_STATUS_CHANGED = "TENANT_STATUS_CHANGED";

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, String entityId, Map<String, Object> details) {
        record(TenantContext.getTenantId(), TenantContext.getUserId(), TenantContext.getUsername(),
                TenantContext.getRole() == null ? null : TenantContext.getRole().name(),
                action, entityType, entityId, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID tenantId,
                       UUID userId,
                       String username,
                       String role,
                       String action,
                       String entityType,
                       String entityId,
                       Map<String, Object> details) {
        AuditLog log = new AuditLog();
        log.setTenantId(tenantId);
        log.setUserId(userId);
        log.setUsername(username);
        log.setRole(role);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        HttpServletRequest request = currentRequest();
        log.setIpAddress(HttpRequestUtils.clientIp(request));
        log.setUserAgent(HttpRequestUtils.userAgent(request));
        auditLogRepository.save(log);
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }
}
