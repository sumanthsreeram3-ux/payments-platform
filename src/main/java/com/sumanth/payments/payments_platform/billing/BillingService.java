package com.sumanth.payments.payments_platform.billing;

import com.sumanth.payments.payments_platform.events.OutboxService;
import com.sumanth.payments.payments_platform.payment.PaymentService;
import com.sumanth.payments.payments_platform.payment.PaymentService.ChargeCommand;
import com.sumanth.payments.payments_platform.payment.PaymentService.ChargeResult;
import com.sumanth.payments.payments_platform.payment.PaymentStatus;
import com.sumanth.payments.payments_platform.plan.BillingInterval;
import com.sumanth.payments.payments_platform.plan.Plan;
import com.sumanth.payments.payments_platform.plan.PlanRepository;
import com.sumanth.payments.payments_platform.subscription.Subscription;
import com.sumanth.payments.payments_platform.subscription.SubscriptionRepository;
import com.sumanth.payments.payments_platform.subscription.SubscriptionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final PaymentService payments;
    private final OutboxService outbox;
    private final int maxAttempts;
    private final long retryBaseDelaySeconds;

    public BillingService(JdbcTemplate jdbc, PlatformTransactionManager txManager,
                          SubscriptionRepository subscriptions, PlanRepository plans,
                          PaymentService payments, OutboxService outbox,
                          @Value("${billing.max-attempts:3}") int maxAttempts,
                          @Value("${billing.retry-base-delay-seconds:10}") long retryBaseDelaySeconds) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.payments = payments;
        this.outbox = outbox;
        this.maxAttempts = maxAttempts;
        this.retryBaseDelaySeconds = retryBaseDelaySeconds;
    }

    /** Bills every subscription that is due. Returns how many were processed. */
    public int runBillingCycle() {
        List<UUID> due = jdbc.queryForList("""
                SELECT id FROM subscriptions
                WHERE status IN ('ACTIVE', 'PAST_DUE')
                  AND COALESCE(next_billing_attempt_at, current_period_end) <= now()
                ORDER BY COALESCE(next_billing_attempt_at, current_period_end)
                LIMIT 100
                """, UUID.class);

        int processed = 0;
        for (UUID id : due) {
            try {
                // One transaction per subscription: a failure here never affects the others.
                Boolean done = tx.execute(status -> billOne(id));
                if (Boolean.TRUE.equals(done)) processed++;
            } catch (Exception e) {
                log.error("Billing failed for subscription {}", id, e);
            }
        }
        return processed;
    }

    private boolean billOne(UUID id) {
        // Lock the row and re-check it is still due (another instance may have just billed it).
        List<UUID> locked = jdbc.queryForList("""
                SELECT id FROM subscriptions
                WHERE id = ? AND status IN ('ACTIVE', 'PAST_DUE')
                  AND COALESCE(next_billing_attempt_at, current_period_end) <= now()
                FOR UPDATE SKIP LOCKED
                """, UUID.class, id);
        if (locked.isEmpty()) return false;

        Subscription sub = subscriptions.findById(id).orElseThrow();
        Plan plan = plans.findById(sub.getPlanId()).orElseThrow();

        boolean cardOk = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT payment_method_valid FROM customers WHERE id = ?", Boolean.class, sub.getCustomerId()));

        // Deterministic key: the same subscription, billing period and attempt always maps to
        // the same key, so a re-run can never charge twice. A retry is a new attempt, so a new key.
        String key = "billing:" + sub.getId() + ":" + sub.getCurrentPeriodEnd().toEpochMilli()
                + ":" + (sub.getBillingAttempts() + 1);

        ChargeResult result = payments.charge(key, new ChargeCommand(
                sub.getCustomerId(), sub.getId(), plan.getAmountMinor(), plan.getCurrency(),
                "Subscription: " + plan.getName(), !cardOk));

        if (result.payment().getStatus() == PaymentStatus.SUCCEEDED) {
            sub.markBilled(addInterval(sub.getCurrentPeriodEnd(), plan.getBillingInterval()));
            outbox.add("subscription", sub.getId(), "subscription.renewed", eventPayload(sub));
            log.info("Renewed subscription {} until {}", sub.getId(), sub.getCurrentPeriodEnd());
        } else {
            // Exponential backoff: base, 2x base, 4x base ...
            long delay = retryBaseDelaySeconds * (1L << sub.getBillingAttempts());
            sub.markBillingFailed(maxAttempts, Instant.now().plusSeconds(delay));
            String type = sub.getStatus() == SubscriptionStatus.CANCELED
                    ? "subscription.canceled" : "subscription.past_due";
            outbox.add("subscription", sub.getId(), type, eventPayload(sub));
            log.warn("Billing failed for subscription {} (attempt {}), now {}",
                    sub.getId(), sub.getBillingAttempts(), sub.getStatus());
        }
        return true;
    }

    private static Instant addInterval(Instant from, BillingInterval interval) {
        ZonedDateTime z = from.atZone(ZoneOffset.UTC);
        return (interval == BillingInterval.MONTHLY ? z.plusMonths(1) : z.plusYears(1)).toInstant();
    }

    private static String eventPayload(Subscription s) {
        return String.format(
                "{\"subscriptionId\":\"%s\",\"customerId\":\"%s\",\"status\":\"%s\",\"billingAttempts\":%d,\"currentPeriodEnd\":\"%s\"}",
                s.getId(), s.getCustomerId(), s.getStatus(), s.getBillingAttempts(), s.getCurrentPeriodEnd());
    }
}