package com.sumanth.payments.payments_platform.it;

import com.sumanth.payments.payments_platform.assistant.AssistantService;
import com.sumanth.payments.payments_platform.assistant.LlmClient;
import com.sumanth.payments.payments_platform.assistant.QueryExceptions;
import com.sumanth.payments.payments_platform.assistant.ReadOnlyQueryRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantIT extends AbstractIntegrationTest {

    @Autowired
    AssistantService assistant;

    @Autowired
    ReadOnlyQueryRunner runner;

    @Test
    void revenueAnswerMatchesTheDatabase() throws Exception {
        UUID customerId = insertCustomer();
        assertEquals(201, postPayment("rev-" + UUID.randomUUID(), paymentJson(customerId, 12_345, false)).statusCode());

        var answer = assistant.ask("What is our total revenue?");

        BigDecimal expected = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount_minor), 0) / 100.0 FROM payments WHERE status = 'SUCCEEDED'",
                BigDecimal.class);
        BigDecimal actual = (BigDecimal) answer.rows().get(0).get(0);
        assertEquals(0, expected.compareTo(actual));
        assertEquals(1, answer.attempts());
    }

    @Test
    void destructiveStatementsNeverReachTheDatabase() {
        long before = count("SELECT count(*) FROM payments");

        assertThrows(QueryExceptions.Rejected.class, () -> runner.run("DELETE FROM payments"));
        assertThrows(QueryExceptions.Rejected.class, () -> runner.run("SELECT 1; DROP TABLE payments"));

        assertEquals(before, count("SELECT count(*) FROM payments"));
    }

    @Test
    void theDatabaseRoleHidesSecretsEvenForQueriesThatPassTheGuard() {
        // These are plain SELECTs, so SqlGuard lets them through. The assistant role is what stops them.
        assertThrows(QueryExceptions.Failed.class, () -> runner.run("SELECT * FROM public.webhook_endpoints"));
        assertThrows(QueryExceptions.Failed.class, () -> runner.run("SELECT * FROM public.idempotency_keys"));
        assertThrows(QueryExceptions.Failed.class, () -> runner.run("SELECT secret FROM webhook_endpoints"));
    }

    @Test
    void slowQueriesAreCancelled() {
        var error = assertThrows(QueryExceptions.Failed.class,
                () -> runner.run("SELECT count(*) FROM generate_series(1, 100000000000)"));
        assertTrue(error.getMessage().toLowerCase().contains("timeout"), error.getMessage());
    }

    @Test
    void resultsAreCappedAtTwoHundredRows() {
        var result = runner.run("SELECT * FROM generate_series(1, 1000)");
        assertEquals(ReadOnlyQueryRunner.MAX_ROWS, result.rows().size());
        assertTrue(result.truncated());
    }

    @Test
    void badSqlIsFedBackToTheModelAndCorrected() {
        AtomicInteger calls = new AtomicInteger();
        List<LlmClient.FailedAttempt> seenOnSecondCall = new ArrayList<>();

        LlmClient flaky = new LlmClient() {
            @Override
            public String generateSql(String schemaPrompt, String question, List<FailedAttempt> failures) {
                if (calls.incrementAndGet() == 1) {
                    return "SELECT no_such_column FROM payments";
                }
                seenOnSecondCall.addAll(failures);
                return "SELECT count(*) AS n FROM payments";
            }

            @Override
            public String summarize(String question, String sql, List<String> columns, List<List<Object>> rows) {
                return "ok";
            }

            @Override
            public String modelName() { return "flaky-test-model"; }
        };

        var answer = new AssistantService(flaky, runner, 1000).ask("how many payments?");

        assertEquals(2, answer.attempts());
        assertEquals(1, seenOnSecondCall.size());
        assertTrue(seenOnSecondCall.get(0).error().contains("no_such_column"));
    }
}