package com.sumanth.payments.payments_platform.webhook;

import com.sumanth.payments.payments_platform.events.OutboxPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "events.kafka-enabled", havingValue = "true", matchIfMissing = true)
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final WebhookService webhooks;

    public WebhookDispatcher(WebhookService webhooks) { this.webhooks = webhooks; }

    @KafkaListener(topics = OutboxPublisher.TOPIC, groupId = "webhook-dispatcher")
    public void onEvent(ConsumerRecord<String, String> record) {
        String eventId = header(record, "event_id");
        String eventType = header(record, "event_type");
        if (eventId == null || eventType == null) {
            log.debug("Skipping message without event headers (published before webhooks existed)");
            return;
        }
        // Idempotent: a redelivered Kafka message cannot create a second webhook.
        webhooks.fanOut(UUID.fromString(eventId), eventType, record.value());
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}