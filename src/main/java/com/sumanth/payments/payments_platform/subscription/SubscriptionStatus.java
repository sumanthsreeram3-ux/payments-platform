package com.sumanth.payments.payments_platform.subscription;

import java.util.Set;

public enum SubscriptionStatus {
    TRIALING, ACTIVE, PAST_DUE, CANCELED;

    public Set<SubscriptionStatus> allowedNext() {
        return switch (this) {
            case TRIALING -> Set.of(ACTIVE, CANCELED);
            case ACTIVE -> Set.of(PAST_DUE, CANCELED);
            case PAST_DUE -> Set.of(ACTIVE, CANCELED);
            case CANCELED -> Set.of();
        };
    }

    public boolean canTransitionTo(SubscriptionStatus next) {
        return allowedNext().contains(next);
    }
}
