package com.sumanth.payments.payments_platform.events;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {

    public static final String TOPIC = "payments.events";
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        // SKIP LOCKED lets several app instances poll without publishing the same row twice.
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, aggregate_id, event_type, payload::text AS payload
                FROM outbox_events
                WHERE published_at IS NULL
                ORDER BY created_at
                LIMIT 100
                FOR UPDATE SKIP LOCKED
                """);

        for (Map<String, Object> row : rows) {
            UUID id = (UUID) row.get("id");
            String key = row.get("aggregate_id").toString();
            String eventType = (String) row.get("event_type");
            String payload = (String) row.get("payload");
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, payload);
                record.headers().add("event_type", eventType.getBytes(StandardCharsets.UTF_8));
                kafka.send(record).get(10, TimeUnit.SECONDS);
                jdbc.update("UPDATE outbox_events SET published_at = now() WHERE id = ?", id);
            } catch (Exception e) {
                // Kafka unavailable: leave this row (and the rest) unpublished and retry next tick.
                log.warn("Could not publish outbox event {}: {}", id, e.toString());
                return;
            }
        }
    }
}