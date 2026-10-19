package com.erpschool.notification.repository;

import com.erpschool.notification.entity.AppNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AppNotificationRepository extends JpaRepository<AppNotification, UUID> {

    Page<AppNotification> findByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, UUID userId, Pageable pageable);

    long countByTenantIdAndUserIdAndReadAtIsNull(UUID tenantId, UUID userId);
}
