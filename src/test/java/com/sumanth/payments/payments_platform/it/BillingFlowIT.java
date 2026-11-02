package com.sumanth.payments.payments_platform.it;

import com.sumanth.payments.payments_platform.billing.BillingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillingFlowIT extends AbstractIntegrationTest {

    @Autowired
    BillingService billing;

    private UUID insertPlan() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO plans (id, name, amount_minor, currency, billing_interval)
                VALUES (?, 'Pro', 49900, 'INR', 'MONTHLY')
                """, id);
        return id;
    }

    /** An ACTIVE subscription whose billing period ended a minute ago. */
    private UUID insertDueSubscription(UUID customerId, UUID planId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO subscriptions
                    (id, customer_id, plan_id, status, current_period_start, current_period_end)
                VALUES (?, ?, ?, 'ACTIVE', now() - interval '1 month', now() - interval '1 minute')
                """, id, customerId, planId);
        return id;
    }

    private String status(UUID subId) {
        return jdbc.queryForObject("SELECT status FROM subscriptions WHERE id = ?", String.class, subId);
    }

    private long attempts(UUID subId) {
        return count("SELECT billing_attempts FROM subscriptions WHERE id = ?", subId);
    }

    private void declineCard(UUID customerId, boolean declined) {
        jdbc.update("UPDATE customers SET payment_method_valid = ? WHERE id = ?", !declined, customerId);
    }

    private void makeRetryDue(UUID subId) {
        jdbc.update("UPDATE subscriptions SET next_billing_attempt_at = now() - interval '1 second' WHERE id = ?",
                subId);
    }

    @Test
    void dueSubscriptionIsRenewedExactlyOnce() {
        UUID customerId = insertCustomer();
        UUID subId = insertDueSubscription(customerId, insertPlan());

        billing.runBillingCycle();
        billing.runBillingCycle();   // a second cycle must not charge again

        assertEquals("ACTIVE", status(subId));
        assertEquals(1, count("SELECT count(*) FROM subscriptions WHERE id = ? AND current_period_end > now()", subId));
        assertEquals(1, count("SELECT count(*) FROM payments WHERE customer_id = ? AND status = 'SUCCEEDED'", customerId));
        assertTrue(ledgerBalanced());
    }

    @Test
    void repeatedDeclinesEndInCancellationWithoutMovingMoney() {
        UUID customerId = insertCustomer();
        UUID subId = insertDueSubscription(customerId, insertPlan());
        declineCard(customerId, true);

        billing.runBillingCycle();
        assertEquals("PAST_DUE", status(subId));
        assertEquals(1, attempts(subId));

        makeRetryDue(subId);
        billing.runBillingCycle();
        assertEquals("PAST_DUE", status(subId));
        assertEquals(2, attempts(subId));

        makeRetryDue(subId);
        billing.runBillingCycle();
        assertEquals("CANCELED", status(subId));
        assertEquals(3, attempts(subId));

        assertEquals(3, count("SELECT count(*) FROM payments WHERE customer_id = ? AND status = 'FAILED'", customerId));
        assertEquals(0, count("SELECT count(*) FROM payments WHERE customer_id = ? AND status = 'SUCCEEDED'", customerId));
        assertTrue(ledgerBalanced());
    }

    @Test
    void pastDueSubscriptionRecoversWhenTheCardIsFixed() {
        UUID customerId = insertCustomer();
        UUID subId = insertDueSubscription(customerId, insertPlan());
        declineCard(customerId, true);

        billing.runBillingCycle();
        assertEquals("PAST_DUE", status(subId));

        declineCard(customerId, false);
        makeRetryDue(subId);
        billing.runBillingCycle();

        assertEquals("ACTIVE", status(subId));
        assertEquals(0, attempts(subId));
        assertEquals(1, count("SELECT count(*) FROM payments WHERE customer_id = ? AND status = 'SUCCEEDED'", customerId));
        assertTrue(ledgerBalanced());
    }
}