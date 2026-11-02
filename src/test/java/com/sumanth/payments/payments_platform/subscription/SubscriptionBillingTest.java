package com.sumanth.payments.payments_platform.subscription;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionBillingTest {

    private Subscription active() {
        Instant now = Instant.now();
        return new Subscription(UUID.randomUUID(), UUID.randomUUID(), SubscriptionStatus.ACTIVE,
                now.minusSeconds(3600), now.minusSeconds(60));
    }

    @Test
    void firstFailureMovesToPastDueAndSchedulesRetry() {
        Subscription s = active();
        Instant retryAt = Instant.now().plusSeconds(10);
        s.markBillingFailed(3, retryAt);
        assertEquals(SubscriptionStatus.PAST_DUE, s.getStatus());
        assertEquals(1, s.getBillingAttempts());
        assertEquals(retryAt, s.getNextBillingAttemptAt());
    }

    @Test
    void thirdFailureCancels() {
        Subscription s = active();
        s.markBillingFailed(3, Instant.now());
        s.markBillingFailed(3, Instant.now());
        s.markBillingFailed(3, Instant.now());
        assertEquals(SubscriptionStatus.CANCELED, s.getStatus());
        assertNotNull(s.getCanceledAt());
        assertNull(s.getNextBillingAttemptAt());
    }

    @Test
    void successAfterFailureRecoversAndResetsRetries() {
        Subscription s = active();
        s.markBillingFailed(3, Instant.now());
        Instant oldEnd = s.getCurrentPeriodEnd();
        Instant newEnd = oldEnd.plusSeconds(30L * 24 * 3600);
        s.markBilled(newEnd);
        assertEquals(SubscriptionStatus.ACTIVE, s.getStatus());
        assertEquals(0, s.getBillingAttempts());
        assertNull(s.getNextBillingAttemptAt());
        assertEquals(oldEnd, s.getCurrentPeriodStart());
        assertEquals(newEnd, s.getCurrentPeriodEnd());
    }
}