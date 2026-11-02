package com.sumanth.payments.payments_platform.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/dev/webhook-sink")
@ConditionalOnProperty(name = "billing.dev-endpoints", havingValue = "true")
public class WebhookSinkController {

    private static final Logger log = LoggerFactory.getLogger(WebhookSinkController.class);

    private final JdbcTemplate jdbc;
    private final AtomicBoolean failing = new AtomicBoolean(false);

    public WebhookSinkController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping
    public ResponseEntity<String> receive(
            @RequestHeader("X-Webhook-Endpoint-Id") UUID endpointId,
            @RequestHeader("X-Webhook-Id") String eventId,
            @RequestHeader("X-Webhook-Event") String eventType,
            @RequestHeader("X-Webhook-Timestamp") long timestamp,
            @RequestHeader("X-Webhook-Signature") String signature,
            @RequestBody String body) {

        if (failing.get()) {
            log.warn("SINK simulated outage: returning 500 for event {}", eventId);
            return ResponseEntity.status(500).body("simulated outage");
        }
        String secret = jdbc.queryForObject("SELECT secret FROM webhook_endpoints WHERE id = ?",
                String.class, endpointId);
        if (!WebhookSigner.verify(secret, timestamp, body, signature, 300, Instant.now().getEpochSecond())) {
            log.warn("SINK rejected event {}: bad signature", eventId);
            return ResponseEntity.status(401).body("invalid signature");
        }
        log.info("SINK RECEIVED {} id={} (signature valid) body={}", eventType, eventId, body);
        return ResponseEntity.ok("ok");
    }

    /** Simulates the customer's server being down (fail=true) or healthy again (fail=false). */
    @PostMapping("/mode")
    public Map<String, Boolean> mode(@RequestParam boolean fail) {
        failing.set(fail);
        return Map.of("failing", fail);
    }
}