package com.sumanth.payments.payments_platform.assistant;

import java.util.List;

public interface LlmClient {

    record FailedAttempt(String sql, String error) {}

    /** Returns one SQL statement (possibly inside a markdown fence) that answers the question. */
    String generateSql(String schemaPrompt, String question, List<FailedAttempt> previousFailures);

    /** Returns a short plain-English answer based only on the rows given. */
    String summarize(String question, String sql, List<String> columns, List<List<Object>> rows);

    String modelName();
}