package com.sumanth.payments.payments_platform.it;

import com.sumanth.payments.payments_platform.webhook.WebhookService;
import com.sumanth.payments.payments_platform.webhook.WebhookService.EndpointCreated;
import com.sumanth.payments.payments_platform.webhook.WebhookSigner;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookDeliveryIT extends AbstractIntegrationTest {

    @Autowired
    WebhookService webhooks;

    private String deliveryStatus(UUID endpointId) {
        return jdbc.queryForObject("SELECT status FROM webhook_deliveries WHERE endpoint_id = ?",
                String.class, endpointId);
    }

    private void makeRetryDue(UUID endpointId) {
        jdbc.update("""
                UPDATE webhook_deliveries SET next_attempt_at = now() - interval '1 second'
                WHERE endpoint_id = ? AND status = 'PENDING'
                """, endpointId);
    }

    @Test
    void failedDeliveriesAreRetriedWithValidSignaturesUntilTheReceiverRecovers() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> received = new CopyOnWriteArrayList<>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hook", exchange -> {
            int n = calls.incrementAndGet();
            byte[] body = exchange.getRequestBody().readAllBytes();
            received.add(exchange.getRequestHeaders().getFirst("X-Webhook-Timestamp") + "|"
                    + exchange.getRequestHeaders().getFirst("X-Webhook-Signature") + "|"
                    + new String(body, StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(n < 3 ? 500 : 200, -1);   // outage for the first two calls
            exchange.close();
        });
        server.start();

        try {
            EndpointCreated endpoint = webhooks.createEndpoint(
                    "http://localhost:" + server.getAddress().getPort() + "/hook", List.of("test.event"));
            UUID eventId = UUID.randomUUID();

            webhooks.fanOut(eventId, "test.event", "{\"hello\":\"world\"}");
            webhooks.fanOut(eventId, "test.event", "{\"hello\":\"world\"}");   // duplicate Kafka message
            assertEquals(1, count("SELECT count(*) FROM webhook_deliveries WHERE endpoint_id = ?", endpoint.id()),
                    "a duplicate event must not create a second delivery");

            webhooks.deliverDue();                       // attempt 1 -> 500
            assertEquals("PENDING", deliveryStatus(endpoint.id()));
            makeRetryDue(endpoint.id());
            webhooks.deliverDue();                       // attempt 2 -> 500
            assertEquals("PENDING", deliveryStatus(endpoint.id()));
            makeRetryDue(endpoint.id());
            webhooks.deliverDue();                       // attempt 3 -> 200

            assertEquals("DELIVERED", deliveryStatus(endpoint.id()));
            assertEquals(3, calls.get());
            assertEquals(3, count("SELECT attempts FROM webhook_deliveries WHERE endpoint_id = ?", endpoint.id()));

            for (String r : received) {
                String[] parts = r.split("\\|", 3);
                assertTrue(WebhookSigner.verify(endpoint.secret(), Long.parseLong(parts[0]), parts[2], parts[1],
                        300, Instant.now().getEpochSecond()), "every attempt must carry a valid signature");
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void endpointsOnlyReceiveTheEventTypesTheySubscribedTo() {
        EndpointCreated endpoint = webhooks.createEndpoint("http://localhost:1/unused", List.of("only.this"));

        webhooks.fanOut(UUID.randomUUID(), "some.other.event", "{}");
        assertEquals(0, count("SELECT count(*) FROM webhook_deliveries WHERE endpoint_id = ?", endpoint.id()));

        webhooks.fanOut(UUID.randomUUID(), "only.this", "{}");
        assertEquals(1, count("SELECT count(*) FROM webhook_deliveries WHERE endpoint_id = ?", endpoint.id()));
    }
}