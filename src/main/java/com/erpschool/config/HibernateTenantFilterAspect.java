package com.erpschool.config;

import com.erpschool.tenant.context.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enables Hibernate tenant filter for school-scoped sessions.
 * ERP Owner has no tenant in context, so the filter stays off (platform scope).
 * School-scoped services still pass tenantId on every query as defense in depth.
 */
@Aspect
@Component
public class HibernateTenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("within(com.erpschool..service..*)")
    public void enableTenantFilter(JoinPoint joinPoint) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return;
        }
        try {
            Session session = entityManager.unwrap(Session.class);
            if (session.getEnabledFilter("tenantFilter") == null) {
                session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
            }
        } catch (IllegalStateException ignored) {
            // No persistence context bound (non-transactional call)
        }
    }
}
