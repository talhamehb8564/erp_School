package com.erpschool.notification.service;

import com.erpschool.common.dto.PageResponse;
import com.erpschool.common.exception.ResourceNotFoundException;
import com.erpschool.common.util.TenantGuard;
import com.erpschool.notification.entity.AppNotification;
import com.erpschool.notification.repository.AppNotificationRepository;
import com.erpschool.tenant.context.TenantContext;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final AppNotificationRepository repository;

    public NotificationService(AppNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void notifyUser(UUID tenantId, UUID userId, String type, String title, String body,
                           String entityType, String entityId) {
        if (userId == null) {
            return;
        }
        AppNotification n = new AppNotification();
        n.setTenantId(tenantId);
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setEntityType(entityType);
        n.setEntityId(entityId);
        n.setCreatedBy(TenantContext.getUserId());
        repository.save(n);
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> mine(Pageable pageable) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        UUID userId = TenantContext.getUserId();
        var page = repository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId, pageable)
                .map(this::toMap);
        return PageResponse.from(page);
    }

    @Transactional
    public Map<String, Object> markRead(UUID id) {
        UUID tenantId = TenantGuard.requireTenantId(null);
        AppNotification n = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        TenantGuard.assertSameTenant(n.getTenantId());
        if (!n.getUserId().equals(TenantContext.getUserId())) {
            throw new ResourceNotFoundException("Notification", id);
        }
        if (!tenantId.equals(n.getTenantId())) {
            throw new ResourceNotFoundException("Notification", id);
        }
        n.setReadAt(Instant.now());
        return toMap(repository.save(n));
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByTenantIdAndUserIdAndReadAtIsNull(
                TenantGuard.requireTenantId(null), TenantContext.getUserId());
    }

    private Map<String, Object> toMap(AppNotification n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", n.getId());
        m.put("type", n.getType());
        m.put("title", n.getTitle());
        m.put("body", n.getBody());
        m.put("entityType", n.getEntityType());
        m.put("entityId", n.getEntityId());
        m.put("readAt", n.getReadAt());
        m.put("createdAt", n.getCreatedAt());
        return m;
    }
}
