package com.sumanth.payments.payments_platform.it;

import com.sumanth.payments.payments_platform.assistant.AssistantService;
import com.sumanth.payments.payments_platform.assistant.ReadOnlyQueryRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Execution accuracy: for each question, the model's query must return the same values as a
 * hand-written reference query. Run with:  $env:ANTHROPIC_API_KEY = "..."; .\gradlew.bat test --tests "*AssistantEvalIT"
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@TestPropertySource(properties = {"assistant.provider=anthropic", "assistant.max-requests-per-minute=1000"})
class AssistantEvalIT extends AbstractIntegrationTest {

    private record Case(String question, String referenceSql) {}

    private static final List<Case> CASES = List.of(
            new Case("What is our total revenue in rupees?",
                    "SELECT COALESCE(SUM(amount_minor), 0) / 100.0 FROM payments WHERE status = 'SUCCEEDED'"),
            new Case("How many payments succeeded?",
                    "SELECT count(*) FROM payments WHERE status = 'SUCCEEDED'"),
            new Case("How many payments failed?",
                    "SELECT count(*) FROM payments WHERE status = 'FAILED'"),
            new Case("What is the revenue in rupees for each customer email?",
                    "SELECT c.email, SUM(p.amount_minor) / 100.0 FROM payments p JOIN customers c ON c.id = p.customer_id WHERE p.status = 'SUCCEEDED' GROUP BY c.email"),
            new Case("What is the largest successful payment in rupees?",
                    "SELECT MAX(amount_minor) / 100.0 FROM payments WHERE status = 'SUCCEEDED'"),
            new Case("What is the average successful payment amount in rupees?",
                    "SELECT AVG(amount_minor) / 100.0 FROM payments WHERE status = 'SUCCEEDED'"),
            new Case("How many subscriptions are there in each status?",
                    "SELECT status, count(*) FROM subscriptions GROUP BY status"),
            new Case("How many subscriptions are past due?",
                    "SELECT count(*) FROM subscriptions WHERE status = 'PAST_DUE'"),
            new Case("How many customers have a declined card?",
                    "SELECT count(*) FROM customers WHERE payment_method_valid = false"),
            new Case("List each plan name with its price in rupees.",
                    "SELECT name, amount_minor / 100.0 FROM plans"),
            new Case("How many customers are there?",
                    "SELECT count(*) FROM customers"),
            new Case("How many failed payments does each customer email have?",
                    "SELECT c.email, count(*) FROM payments p JOIN customers c ON c.id = p.customer_id WHERE p.status = 'FAILED' GROUP BY c.email"),
            new Case("What is the total of all debit ledger entries in rupees?",
                    "SELECT COALESCE(SUM(amount_minor), 0) / 100.0 FROM ledger_entries WHERE direction = 'DEBIT'"),
            new Case("What is the net debit balance in rupees of each ledger account?",
                    "SELECT account, SUM(CASE WHEN direction = 'DEBIT' THEN amount_minor ELSE -amount_minor END) / 100.0 FROM ledger_entries GROUP BY account"),
            new Case("How many webhook deliveries are pending?",
                    "SELECT count(*) FROM webhook_deliveries WHERE status = 'PENDING'"));

    @Autowired
    AssistantService assistant;

    @Autowired
    ReadOnlyQueryRunner runner;

    @Test
    void measureExecutionAccuracy() throws Exception {
        seed();

        int correct = 0;
        List<String> report = new ArrayList<>();
        for (Case c : CASES) {
            String verdict;
            try {
                var answer = assistant.ask(c.question());
                List<String> expected = normalize(runner.run(c.referenceSql()).rows());
                List<String> actual = normalize(answer.rows());
                if (expected.equals(actual)) {
                    correct++;
                    verdict = "PASS (attempts=" + answer.attempts() + ")";
                } else {
                    verdict = "FAIL  expected=" + expected + " actual=" + actual + "  sql=" + answer.sql();
                }
            } catch (Exception e) {
                verdict = "ERROR " + e.getMessage();
            }
            report.add(verdict + "  |  " + c.question());
        }

        double accuracy = (double) correct / CASES.size();
        String summary = String.format("Execution accuracy: %d/%d = %.0f%%%n%s", correct, CASES.size(),
                accuracy * 100, String.join("\n", report));
        Path out = Path.of("build", "assistant-eval.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, summary);
        System.out.println(summary);

        assertTrue(accuracy >= 0.6, "accuracy fell below 60%:\n" + summary);
    }

    private void seed() throws Exception {
        UUID a = insertCustomer();
        UUID b = insertCustomer();
        UUID c = insertCustomer();
        postPayment("eval-" + UUID.randomUUID(), paymentJson(a, 49_900, false));
        postPayment("eval-" + UUID.randomUUID(), paymentJson(a, 20_000, false));
        postPayment("eval-" + UUID.randomUUID(), paymentJson(b, 99_900, false));
        postPayment("eval-" + UUID.randomUUID(), paymentJson(b, 15_000, true));
        postPayment("eval-" + UUID.randomUUID(), paymentJson(c, 7_500, true));
        UUID plan = UUID.randomUUID();
        jdbc.update("INSERT INTO plans (id, name, amount_minor, currency, billing_interval) VALUES (?, 'Eval Plan', 49900, 'INR', 'MONTHLY')", plan);
        for (String status : List.of("ACTIVE", "PAST_DUE", "CANCELED")) {
            jdbc.update("""
                    INSERT INTO subscriptions (id, customer_id, plan_id, status, current_period_start, current_period_end)
                    VALUES (?, ?, ?, ?, now(), now() + interval '1 month')
                    """, UUID.randomUUID(), a, plan, status);
        }
    }

    private static List<String> normalize(List<List<Object>> rows) {
        return rows.stream()
                .map(row -> row.stream().map(AssistantEvalIT::cell).collect(Collectors.joining("|")))
                .sorted()
                .toList();
    }

    private static String cell(Object value) {
        if (value == null) return "null";
        if (value instanceof Number n) return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
        return value.toString();
    }
}