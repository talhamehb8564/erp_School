package com.erpschool.subscription.repository;

import com.erpschool.subscription.entity.SubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, UUID> {

    List<SubscriptionPayment> findBySubscriptionIdOrderBySubmittedAtDesc(UUID subscriptionId);
}
