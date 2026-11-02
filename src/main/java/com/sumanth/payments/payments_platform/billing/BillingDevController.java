package com.sumanth.payments.payments_platform.billing;

import com.sumanth.payments.payments_platform.common.NotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/billing")
@ConditionalOnProperty(name = "billing.dev-endpoints", havingValue = "true")
public class BillingDevController {

    private final BillingService billing;
    private final JdbcTemplate jdbc;

    public BillingDevController(BillingService billing, JdbcTemplate jdbc) {
        this.billing = billing;
        this.jdbc = jdbc;
    }

    /** Run a billing cycle right now instead of waiting for the scheduler. */
    @PostMapping("/run")
    public Map<String, Integer> run() {
        return Map.of("processed", billing.runBillingCycle());
    }

    /** Pretend the subscription's period ended a minute ago, so it becomes due. */
    @PostMapping("/dev/subscriptions/{id}/make-due")
    public Map<String, Object> makeDue(@PathVariable UUID id) {
        int n = jdbc.update("""
                UPDATE subscriptions
                SET current_period_start = now() - interval '1 month',
                    current_period_end   = now() - interval '1 minute'
                WHERE id = ? AND status = 'ACTIVE'
                """, id);
        if (n == 0) throw new NotFoundException("No ACTIVE subscription with that id");
        return Map.of("subscriptionId", id, "due", true);
    }

    /** Simulate a card that is declined (valid=false) or fixed (valid=true). */
    @PostMapping("/dev/customers/{id}/payment-method")
    public Map<String, Object> setPaymentMethod(@PathVariable UUID id, @RequestParam boolean valid) {
        int n = jdbc.update("UPDATE customers SET payment_method_valid = ? WHERE id = ?", valid, id);
        if (n == 0) throw new NotFoundException("Customer not found");
        return Map.of("customerId", id, "paymentMethodValid", valid);
    }
}