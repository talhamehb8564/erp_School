package com.erpschool.subscription;

import com.erpschool.subscription.entity.Subscription;
import com.erpschool.subscription.entity.SubscriptionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionExpiryTest {

    @Test
    void paidSubscriptionExpiresAfterPeriodEnd() {
        Subscription s = new Subscription();
        s.setStatus(SubscriptionStatus.PAID);
        s.setPeriodEnd(LocalDate.of(2026, 9, 1));
        assertThat(s.isPeriodExpired(LocalDate.of(2026, 9, 2))).isTrue();
        assertThat(s.isPeriodExpired(LocalDate.of(2026, 9, 1))).isFalse();
    }

    @Test
    void pendingSubscriptionDoesNotExpireByDate() {
        Subscription s = new Subscription();
        s.setStatus(SubscriptionStatus.PENDING);
        s.setPeriodEnd(LocalDate.of(2026, 1, 1));
        assertThat(s.isPeriodExpired(LocalDate.of(2026, 9, 19))).isFalse();
    }
}
