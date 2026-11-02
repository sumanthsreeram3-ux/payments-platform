package com.sumanth.payments.payments_platform.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookService {

    public record EndpointView(UUID id, String url, List<String> eventTypes, boolean active) {}

    public record EndpointCreated(UUID id, String url, List<String> eventTypes, String secret) {}

    public record DeliveryView(UUID id, UUID eventId, String eventType, String status, int attempts,
                               Instant nextAttemptAt, Integer lastStatusCode, String lastError) {}

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();   // redirects are NOT followed, which is the safe default for webhooks
    private final SecureRandom random = new SecureRandom();
    private final int maxAttempts;
    private final long retryBaseDelaySeconds;

    public WebhookService(JdbcTemplate jdbc, PlatformTransactionManager txManager,
                          @Value("${webhooks.max-attempts:6}") int maxAttempts,
                          @Value("${webhooks.retry-base-delay-seconds:5}") long retryBaseDelaySeconds) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.maxAttempts = maxAttempts;
        this.retryBaseDelaySeconds = retryBaseDelaySeconds;
    }

    // ---------- endpoint management ----------

    public EndpointCreated createEndpoint(String url, List<String> eventTypes) {
        UUID id = UUID.randomUUID();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String secret = "whsec_" + HexFormat.of().formatHex(bytes);
        List<String> types = eventTypes == null ? List.of() : eventTypes;
        jdbc.update("INSERT INTO webhook_endpoints (id, url, secret, event_types) VALUES (?, ?, ?, ?)",
                id, url, secret, types.isEmpty() ? null : String.join(",", types));
        return new EndpointCreated(id, url, types, secret);
    }

    public List<EndpointView> listEndpoints() {
        return jdbc.query("SELECT id, url, event_types, active FROM webhook_endpoints ORDER BY created_at",
                (rs, i) -> new EndpointView(rs.getObject("id", UUID.class), rs.getString("url"),
                        splitTypes(rs.getString("event_types")), rs.getBoolean("active")));
    }

    public List<DeliveryView> listDeliveries(UUID endpointId) {
        return jdbc.query("""
                SELECT id, event_id, event_type, status, attempts, next_attempt_at, last_status_code, last_error
                FROM webhook_deliveries
                WHERE endpoint_id = ?
                ORDER BY created_at DESC
                LIMIT 50
                """,
                (rs, i) -> new DeliveryView(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
                        rs.getString("event_type"), rs.getString("status"), rs.getInt("attempts"),
                        toInstant(rs.getObject("next_attempt_at", OffsetDateTime.class)),
                        (Integer) rs.getObject("last_status_code"), rs.getString("last_error")),
                endpointId);
    }

    // ---------- fan-out (called for every Kafka message) ----------

    /** Creates one pending delivery per matching endpoint. Safe to call twice for the same event. */
    public int fanOut(UUID eventId, String eventType, String payloadJson) {
        return jdbc.update("""
                INSERT INTO webhook_deliveries (id, endpoint_id, event_id, event_type, payload, status, next_attempt_at)
                SELECT gen_random_uuid(), e.id, ?, ?, ?::jsonb, 'PENDING', now()
                FROM webhook_endpoints e
                WHERE e.active
                  AND (e.event_types IS NULL OR (',' || e.event_types || ',') LIKE ('%,' || ?::text || ',%'))
                ON CONFLICT (endpoint_id, event_id) DO NOTHING
                """, eventId, eventType, payloadJson, eventType);
    }

    // ---------- delivery worker ----------

    /** Attempts every delivery that is due. Returns how many were attempted. */
    public int deliverDue() {
        List<UUID> due = jdbc.queryForList("""
                SELECT id FROM webhook_deliveries
                WHERE status = 'PENDING' AND next_attempt_at <= now()
                ORDER BY next_attempt_at
                LIMIT 50
                """, UUID.class);

        int attempted = 0;
        for (UUID id : due) {
            try {
                Boolean done = tx.execute(status -> deliverOne(id));
                if (Boolean.TRUE.equals(done)) attempted++;
            } catch (Exception e) {
                log.error("Webhook delivery {} crashed", id, e);
            }
        }
        return attempted;
    }

    private boolean deliverOne(UUID id) {
        // Lock the row so two app instances never send the same delivery at once.
        // Note: the lock is held during the HTTP call (max ~5s). At larger scale you would
        // claim rows with a lease instead, so slow receivers don't hold DB connections.
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT d.id, d.event_id, d.event_type, d.payload::text AS payload, d.attempts,
                       e.id AS endpoint_id, e.url, e.secret
                FROM webhook_deliveries d
                JOIN webhook_endpoints e ON e.id = d.endpoint_id
                WHERE d.id = ? AND d.status = 'PENDING' AND d.next_attempt_at <= now()
                FOR UPDATE OF d SKIP LOCKED
                """, id);
        if (rows.isEmpty()) return false;

        Map<String, Object> row = rows.get(0);
        String url = (String) row.get("url");
        String secret = (String) row.get("secret");
        String body = (String) row.get("payload");
        UUID eventId = (UUID) row.get("event_id");
        int attempts = ((Number) row.get("attempts")).intValue() + 1;

        long timestamp = Instant.now().getEpochSecond();
        Integer statusCode = null;
        String error = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Id", eventId.toString())
                    .header("X-Webhook-Event", (String) row.get("event_type"))
                    .header("X-Webhook-Endpoint-Id", row.get("endpoint_id").toString())
                    .header("X-Webhook-Timestamp", String.valueOf(timestamp))
                    .header("X-Webhook-Signature", WebhookSigner.sign(secret, timestamp, body))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            statusCode = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (statusCode < 200 || statusCode >= 300) error = "HTTP " + statusCode;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = "interrupted";
        } catch (Exception e) {
            error = truncate(e.toString());
        }

        if (error == null) {
            jdbc.update("""
                    UPDATE webhook_deliveries
                    SET status = 'DELIVERED', attempts = ?, last_status_code = ?, last_error = NULL,
                        delivered_at = now(), next_attempt_at = NULL
                    WHERE id = ?
                    """, attempts, statusCode, id);
            log.info("Webhook {} delivered to {} on attempt {}", eventId, url, attempts);
        } else if (attempts >= maxAttempts) {
            jdbc.update("""
                    UPDATE webhook_deliveries
                    SET status = 'FAILED', attempts = ?, last_status_code = ?, last_error = ?, next_attempt_at = NULL
                    WHERE id = ?
                    """, attempts, statusCode, error, id);
            log.warn("Webhook {} to {} gave up after {} attempts: {}", eventId, url, attempts, error);
        } else {
            double delaySeconds = retryBaseDelaySeconds * (double) (1L << (attempts - 1));   // 5s, 10s, 20s...
            jdbc.update("""
                    UPDATE webhook_deliveries
                    SET attempts = ?, last_status_code = ?, last_error = ?,
                        next_attempt_at = now() + ? * interval '1 second'
                    WHERE id = ?
                    """, attempts, statusCode, error, delaySeconds, id);
            log.warn("Webhook {} to {} failed (attempt {}): {}. Retrying in {}s", eventId, url, attempts, error,
                    (long) delaySeconds);
        }
        return true;
    }

    private static List<String> splitTypes(String csv) {
        return csv == null || csv.isBlank() ? List.of() : Arrays.asList(csv.split(","));
    }

    private static Instant toInstant(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }

    private static String truncate(String s) {
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}