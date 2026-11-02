package com.sumanth.payments.payments_platform.webhook;

import com.sumanth.payments.payments_platform.webhook.WebhookService.EndpointCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/dev/webhook-sink")
@ConditionalOnProperty(name = "billing.dev-endpoints", havingValue = "true")
public class WebhookSinkController {

    private static final Logger log = LoggerFactory.getLogger(WebhookSinkController.class);

    private final JdbcTemplate jdbc;
    private final WebhookService webhooks;
    private final int port;
    private final AtomicBoolean failing = new AtomicBoolean(false);

    public WebhookSinkController(JdbcTemplate jdbc, WebhookService webhooks,
                                 @Value("${server.port:8080}") int port) {
        this.jdbc = jdbc;
        this.webhooks = webhooks;
        this.port = port;
    }

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
    public Map<String, Boolean> setMode(@RequestParam boolean fail) {
        failing.set(fail);
        return Map.of("failing", fail);
    }

    @GetMapping("/mode")
    public Map<String, Boolean> getMode() {
        return Map.of("failing", failing.get());
    }

    /** One-click demo setup: registers this sink as a webhook endpoint (if it is not already). */
    @PostMapping("/register")
    public Map<String, Object> register() {
        String url = "http://localhost:" + port + "/dev/webhook-sink";
        List<UUID> existing = jdbc.queryForList("SELECT id FROM webhook_endpoints WHERE url = ?", UUID.class, url);
        if (!existing.isEmpty()) {
            return Map.of("id", existing.get(0), "url", url, "alreadyRegistered", true);
        }
        EndpointCreated created = webhooks.createEndpoint(url, List.of());   // empty list = all events
        return Map.of("id", created.id(), "url", url, "alreadyRegistered", false);
    }
}