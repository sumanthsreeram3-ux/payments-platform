package com.sumanth.payments.payments_platform.assistant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "assistant.provider", havingValue = "stub", matchIfMissing = true)
public class StubLlmClient implements LlmClient {

    @Override
    public String generateSql(String schemaPrompt, String question, List<FailedAttempt> previousFailures) {
        String q = question.toLowerCase(Locale.ROOT);

        if (has(q, "webhook")) {
            return "SELECT status, count(*) AS deliveries FROM webhook_deliveries GROUP BY status ORDER BY status";
        }
        if (has(q, "ledger", "balance", "account")) {
            return """
                    SELECT account,
                           SUM(CASE WHEN direction = 'DEBIT' THEN amount_minor ELSE -amount_minor END) / 100.0 AS net_debit_inr
                    FROM ledger_entries GROUP BY account ORDER BY account""";
        }
        if (has(q, "past due", "past_due", "overdue")) {
            return "SELECT count(*) AS past_due_subscriptions FROM subscriptions WHERE status = 'PAST_DUE'";
        }
        if (has(q, "subscription")) {
            return "SELECT status, count(*) AS subscriptions FROM subscriptions GROUP BY status ORDER BY status";
        }
        if (has(q, "fail")) {
            if (has(q, "how many", "count", "number")) {
                return "SELECT count(*) AS failed_payments FROM payments WHERE status = 'FAILED'";
            }
            return """
                    SELECT p.created_at, c.email, p.amount_minor / 100.0 AS amount_inr, p.description
                    FROM payments p JOIN customers c ON c.id = p.customer_id
                    WHERE p.status = 'FAILED' ORDER BY p.created_at DESC LIMIT 20""";
        }
        if (has(q, "revenue", "sales", "earn")) {
            if (has(q, "customer")) {
                return """
                        SELECT c.email, SUM(p.amount_minor) / 100.0 AS revenue_inr
                        FROM payments p JOIN customers c ON c.id = p.customer_id
                        WHERE p.status = 'SUCCEEDED' GROUP BY c.email ORDER BY revenue_inr DESC LIMIT 50""";
            }
            return "SELECT COALESCE(SUM(amount_minor), 0) / 100.0 AS total_revenue_inr FROM payments WHERE status = 'SUCCEEDED'";
        }
        if (has(q, "plan")) {
            return "SELECT name, amount_minor / 100.0 AS price_inr, billing_interval FROM plans ORDER BY name";
        }
        if (has(q, "customer")) {
            if (has(q, "how many", "count", "number")) {
                return "SELECT count(*) AS customers FROM customers";
            }
            return "SELECT email, name, payment_method_valid FROM customers ORDER BY created_at DESC LIMIT 50";
        }
        return "SELECT status, count(*) AS payments, COALESCE(SUM(amount_minor), 0) / 100.0 AS total_inr "
                + "FROM payments GROUP BY status ORDER BY status";
    }

    @Override
    public String summarize(String question, String sql, List<String> columns, List<List<Object>> rows) {
        if (rows.isEmpty()) return "No matching rows were found.";
        if (rows.size() == 1 && columns.size() == 1) {
            return columns.get(0).replace('_', ' ') + ": " + rows.get(0).get(0) + ".";
        }
        return "Found " + rows.size() + (rows.size() == 1 ? " row" : " rows") + ". First row: "
                + describe(columns, rows.get(0)) + ".";
    }

    @Override
    public String modelName() { return "stub-rules-v1"; }

    private static boolean has(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private static String describe(List<String> columns, List<Object> row) {
        return java.util.stream.IntStream.range(0, columns.size())
                .mapToObj(i -> columns.get(i) + " = " + row.get(i))
                .collect(Collectors.joining(", "));
    }
}