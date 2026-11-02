package com.sumanth.payments.payments_platform.it;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Testcontainers starts a throwaway Postgres 16 and keeps it for the whole test run.
                "spring.datasource.url=jdbc:tc:postgresql:16:///payments?TC_DAEMON=true",
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
                "events.kafka-enabled=false",
                "billing.scheduler-enabled=false",
                "webhooks.scheduler-enabled=false",
                "billing.dev-endpoints=false"
        })
public abstract class AbstractIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    @Value("${local.server.port}")
    protected int port;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    protected UUID insertCustomer() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO customers (id, email, name) VALUES (?, ?, ?)",
                id, id + "@test.dev", "Test " + id);
        return id;
    }

    protected String paymentJson(UUID customerId, long amountMinor, boolean simulateFailure) {
        return String.format(
                "{\"customerId\":\"%s\",\"amountMinor\":%d,\"currency\":\"INR\",\"description\":\"it\",\"simulateFailure\":%b}",
                customerId, amountMinor, simulateFailure);
    }

    protected HttpResponse<String> postPayment(String idempotencyKey, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    /** Every debit must be matched by an equal credit across the whole ledger. */
    protected boolean ledgerBalanced() {
        Long net = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amount_minor
                                         ELSE -amount_minor END), 0)
                FROM ledger_entries
                """, Long.class);
        return net != null && net == 0;
    }
}