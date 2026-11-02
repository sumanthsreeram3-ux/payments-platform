package com.sumanth.payments.payments_platform.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "billing.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class BillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);

    private final BillingService billing;

    public BillingScheduler(BillingService billing) { this.billing = billing; }

    @Scheduled(fixedDelayString = "${billing.poll-interval-ms:5000}")
    public void tick() {
        int processed = billing.runBillingCycle();
        if (processed > 0) log.info("Billing cycle processed {} subscription(s)", processed);
    }
}