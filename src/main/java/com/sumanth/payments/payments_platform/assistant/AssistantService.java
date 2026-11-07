package com.sumanth.payments.payments_platform.assistant;

import com.sumanth.payments.payments_platform.assistant.ReadOnlyQueryRunner.QueryResult;
import com.sumanth.payments.payments_platform.common.UnprocessableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Service
public class AssistantService {

    public static final int MAX_ATTEMPTS = 3;

    public record Answer(String question, String sql, List<String> columns, List<List<Object>> rows,
                         boolean truncated, String answer, int attempts, String model) {}

    static final String SCHEMA_PROMPT = """
            You are a careful analytics assistant for a payments platform. Answer each question by writing
            ONE PostgreSQL SELECT statement.

            Rules:
            - Output only the SQL. No explanation, no markdown, no comments.
            - One statement, SELECT or WITH ... SELECT only. No semicolons inside it, no double quotes.
            - Use only these views. The search path already points at them, so do not add a schema prefix:
              customers(id, email, name, payment_method_valid, created_at)
              plans(id, name, amount_minor, currency, billing_interval, active, created_at)
                -- billing_interval: MONTHLY | YEARLY
              subscriptions(id, customer_id, plan_id, status, current_period_start, current_period_end,
                            canceled_at, billing_attempts, next_billing_attempt_at, created_at)
                -- status: TRIALING | ACTIVE | PAST_DUE | CANCELED
              payments(id, customer_id, subscription_id, amount_minor, currency, status, description, created_at)
                -- status: SUCCEEDED | FAILED
              ledger_entries(id, transaction_id, account, account_type, direction, amount_minor, currency,
                             description, created_at)
                -- direction: DEBIT | CREDIT; accounts: CASH, REVENUE
              webhook_deliveries(id, event_type, status, attempts, last_status_code, created_at, delivered_at)
                -- status: PENDING | DELIVERED | FAILED
            - Money is stored in minor units (paise). Divide by 100.0 (with the decimal point, never 100)
              to get rupees, and name such columns with an _inr suffix.
            - Timestamps are timestamptz in UTC; use now() for the current time.
            - Show customer emails by joining through customer_id instead of showing ids.
            - Add ORDER BY where it helps and keep results under 200 rows.""";

    private final LlmClient llm;
    private final ReadOnlyQueryRunner runner;
    private final int maxPerMinute;
    private final Deque<Long> recent = new ArrayDeque<>();

    public AssistantService(LlmClient llm, ReadOnlyQueryRunner runner,
                            @Value("${assistant.max-requests-per-minute:30}") int maxPerMinute) {
        this.llm = llm;
        this.runner = runner;
        this.maxPerMinute = maxPerMinute;
    }

    public Answer ask(String question) {
        checkRate();
        List<LlmClient.FailedAttempt> failures = new ArrayList<>();
        String lastError = "unknown error";

        try {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                String sql = "";
                try {
                    sql = SqlGuard.extractSql(llm.generateSql(SCHEMA_PROMPT, question, List.copyOf(failures)));
                    QueryResult result = runner.run(sql);
                    String summary = llm.summarize(question, sql, result.columns(), result.rows());
                    return new Answer(question, sql, result.columns(), result.rows(), result.truncated(),
                            summary, attempt, llm.modelName());
                } catch (QueryExceptions.Rejected | QueryExceptions.Failed e) {
                    lastError = e.getMessage();
                    // Give the model its own SQL and the exact error so it can correct itself.
                    failures.add(new LlmClient.FailedAttempt(sql, lastError));
                }
            }
        } catch (LlmException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The language model is unavailable: " + e.getMessage());
        }
        throw new UnprocessableException(
                "I could not turn that into a valid, read-only query. Try rephrasing. Last error: " + lastError);
    }

    /** Protects the model API key: a global cap on questions per minute. */
    private synchronized void checkRate() {
        long now = System.currentTimeMillis();
        while (!recent.isEmpty() && now - recent.peekFirst() > 60_000) {
            recent.pollFirst();
        }
        if (recent.size() >= maxPerMinute) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many questions, try again in a minute");
        }
        recent.addLast(now);
    }
}