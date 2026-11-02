package com.sumanth.payments.payments_platform.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "webhooks.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class WebhookDeliveryScheduler {

    private final WebhookService webhooks;

    public WebhookDeliveryScheduler(WebhookService webhooks) { this.webhooks = webhooks; }

    @Scheduled(fixedDelayString = "${webhooks.poll-interval-ms:2000}")
    public void tick() {
        webhooks.deliverDue();
    }
}