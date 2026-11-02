package com.sumanth.payments.payments_platform.subscription;

import com.sumanth.payments.payments_platform.common.ConflictException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {
    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "billing_attempts", nullable = false)
    private int billingAttempts;

    @Column(name = "next_billing_attempt_at")
    private Instant nextBillingAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Subscription() {}

    public Subscription(UUID customerId, UUID planId, SubscriptionStatus status,
                        Instant periodStart, Instant periodEnd) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.planId = planId;
        this.status = status;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
        this.createdAt = Instant.now();
    }

    public void transitionTo(SubscriptionStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new ConflictException("Cannot move subscription from " + status + " to " + next);
        }
        this.status = next;
        if (next == SubscriptionStatus.CANCELED) {
            this.canceledAt = Instant.now();
        }
    }

    /** A renewal charge succeeded: recover if past due, move to the next period, reset retries. */
    public void markBilled(Instant newPeriodEnd) {
        if (status == SubscriptionStatus.PAST_DUE) {
            transitionTo(SubscriptionStatus.ACTIVE);
        }
        this.currentPeriodStart = this.currentPeriodEnd;
        this.currentPeriodEnd = newPeriodEnd;
        this.billingAttempts = 0;
        this.nextBillingAttemptAt = null;
    }

    /** A renewal charge failed: schedule a retry, or cancel after maxAttempts. */
    public void markBillingFailed(int maxAttempts, Instant nextAttemptAt) {
        this.billingAttempts++;
        if (billingAttempts >= maxAttempts) {
            transitionTo(SubscriptionStatus.CANCELED);
            this.nextBillingAttemptAt = null;
        } else {
            if (status == SubscriptionStatus.ACTIVE) {
                transitionTo(SubscriptionStatus.PAST_DUE);
            }
            this.nextBillingAttemptAt = nextAttemptAt;
        }
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getPlanId() { return planId; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public Instant getCanceledAt() { return canceledAt; }
    public int getBillingAttempts() { return billingAttempts; }
    public Instant getNextBillingAttemptAt() { return nextBillingAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
}