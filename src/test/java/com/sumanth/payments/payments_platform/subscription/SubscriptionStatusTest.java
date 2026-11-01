package com.sumanth.payments.payments_platform.subscription;

import org.junit.jupiter.api.Test;
import static com.sumanth.payments.payments_platform.subscription.SubscriptionStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class SubscriptionStatusTest {

    @Test
    void canceledIsTerminal() {
        for (SubscriptionStatus next : values()) {
            assertFalse(CANCELED.canTransitionTo(next));
        }
    }

    @Test
    void activeCanGoPastDueAndBack() {
        assertTrue(ACTIVE.canTransitionTo(PAST_DUE));
        assertTrue(PAST_DUE.canTransitionTo(ACTIVE));
    }

    @Test
    void trialingCannotGoStraightToPastDue() {
        assertFalse(TRIALING.canTransitionTo(PAST_DUE));
    }
}