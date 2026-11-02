package com.sumanth.payments.payments_platform.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    public record Summary(long customers, long activeSubscriptions, long pastDueSubscriptions,
                          long canceledSubscriptions, long succeededPayments, long failedPayments,
                          long revenueMinor, long pendingOutboxEvents, long pendingWebhooks,
                          long failedWebhooks, boolean ledgerBalanced) {}

    public record CustomerRow(UUID id, String email, String name, boolean paymentMethodValid) {}

    public record PaymentRow(UUID id, String customerEmail, long amountMinor, String currency,
                             String status, String description, Instant createdAt) {}

    public record SubscriptionRow(UUID id, String customerEmail, String planName, String status,
                                  int billingAttempts, Instant nextBillingAttemptAt,
                                  Instant currentPeriodEnd) {}

    public record DeliveryRow(UUID id, String endpointUrl, String eventType, String status, int attempts,
                              Integer lastStatusCode, Instant nextAttemptAt, Instant createdAt) {}

    private final JdbcTemplate jdbc;

    public DashboardController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/summary")
    public Summary summary() {
        return jdbc.queryForObject("""
                SELECT
                  (SELECT count(*) FROM customers)                                            AS customers,
                  (SELECT count(*) FROM subscriptions WHERE status = 'ACTIVE')                AS active_subs,
                  (SELECT count(*) FROM subscriptions WHERE status = 'PAST_DUE')              AS past_due_subs,
                  (SELECT count(*) FROM subscriptions WHERE status = 'CANCELED')              AS canceled_subs,
                  (SELECT count(*) FROM payments WHERE status = 'SUCCEEDED')                  AS succeeded,
                  (SELECT count(*) FROM payments WHERE status = 'FAILED')                     AS failed,
                  (SELECT COALESCE(SUM(amount_minor), 0) FROM payments WHERE status = 'SUCCEEDED') AS revenue,
                  (SELECT count(*) FROM outbox_events WHERE published_at IS NULL)             AS pending_outbox,
                  (SELECT count(*) FROM webhook_deliveries WHERE status = 'PENDING')          AS pending_hooks,
                  (SELECT count(*) FROM webhook_deliveries WHERE status = 'FAILED')           AS failed_hooks,
                  (SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amount_minor
                                            ELSE -amount_minor END), 0) FROM ledger_entries)  AS ledger_net
                """,
                (rs, i) -> new Summary(rs.getLong("customers"), rs.getLong("active_subs"),
                        rs.getLong("past_due_subs"), rs.getLong("canceled_subs"), rs.getLong("succeeded"),
                        rs.getLong("failed"), rs.getLong("revenue"), rs.getLong("pending_outbox"),
                        rs.getLong("pending_hooks"), rs.getLong("failed_hooks"), rs.getLong("ledger_net") == 0));
    }

    @GetMapping("/customers")
    public List<CustomerRow> customers() {
        return jdbc.query("""
                SELECT id, email, name, payment_method_valid
                FROM customers ORDER BY created_at DESC LIMIT 100
                """,
                (rs, i) -> new CustomerRow(rs.getObject("id", UUID.class), rs.getString("email"),
                        rs.getString("name"), rs.getBoolean("payment_method_valid")));
    }

    @GetMapping("/payments")
    public List<PaymentRow> payments() {
        return jdbc.query("""
                SELECT p.id, c.email, p.amount_minor, p.currency, p.status, p.description, p.created_at
                FROM payments p JOIN customers c ON c.id = p.customer_id
                ORDER BY p.created_at DESC LIMIT 25
                """,
                (rs, i) -> new PaymentRow(rs.getObject("id", UUID.class), rs.getString("email"),
                        rs.getLong("amount_minor"), rs.getString("currency"), rs.getString("status"),
                        rs.getString("description"), instant(rs, "created_at")));
    }

    @GetMapping("/subscriptions")
    public List<SubscriptionRow> subscriptions() {
        return jdbc.query("""
                SELECT s.id, c.email, pl.name AS plan_name, s.status, s.billing_attempts,
                       s.next_billing_attempt_at, s.current_period_end
                FROM subscriptions s
                JOIN customers c ON c.id = s.customer_id
                JOIN plans pl ON pl.id = s.plan_id
                ORDER BY s.created_at DESC LIMIT 25
                """,
                (rs, i) -> new SubscriptionRow(rs.getObject("id", UUID.class), rs.getString("email"),
                        rs.getString("plan_name"), rs.getString("status"), rs.getInt("billing_attempts"),
                        instant(rs, "next_billing_attempt_at"), instant(rs, "current_period_end")));
    }

    @GetMapping("/webhook-deliveries")
    public List<DeliveryRow> webhookDeliveries() {
        return jdbc.query("""
                SELECT d.id, e.url, d.event_type, d.status, d.attempts, d.last_status_code,
                       d.next_attempt_at, d.created_at
                FROM webhook_deliveries d JOIN webhook_endpoints e ON e.id = d.endpoint_id
                ORDER BY d.created_at DESC LIMIT 25
                """,
                (rs, i) -> new DeliveryRow(rs.getObject("id", UUID.class), rs.getString("url"),
                        rs.getString("event_type"), rs.getString("status"), rs.getInt("attempts"),
                        rs.getObject("last_status_code", Integer.class),
                        instant(rs, "next_attempt_at"), instant(rs, "created_at")));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime t = rs.getObject(column, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }
}