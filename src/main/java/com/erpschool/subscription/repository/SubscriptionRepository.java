package com.erpschool.subscription.repository;

import com.erpschool.subscription.entity.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Page<Subscription> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<Subscription> findTopByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Page<Subscription> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
