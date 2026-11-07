package com.sumanth.payments.payments_platform.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT 1",
            "select * from payments where status = 'FAILED';",
            "WITH t AS (SELECT status, count(*) AS n FROM payments GROUP BY status) SELECT * FROM t",
            "SELECT * FROM payments WHERE description ILIKE '%drop table%'",
            "SELECT 'it''s fine' AS note",
            "SELECT created_at, canceled_at FROM subscriptions"
    })
    void acceptsPlainReadOnlyQueries(String sql) {
        assertDoesNotThrow(() -> SqlGuard.validate(sql));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM payments",
            "UPDATE payments SET status = 'SUCCEEDED'",
            "DROP TABLE payments",
            "SELECT 1; DROP TABLE payments",
            "SELECT 1; ; SELECT 2",
            "SELECT 1 -- hidden",
            "SELECT 1 /* hidden */",
            "SELECT pg_sleep(10)",
            "SELECT * FROM pg_user",
            "SELECT set_config('role', 'none', true)",
            "SELECT * INTO backup FROM payments",
            "WITH d AS (DELETE FROM payments RETURNING *) SELECT * FROM d",
            "SELECT \"Payments\".* FROM payments",
            "SELECT $$x$$",
            "SELECT E'\\x41'",
            "SELECT 'unterminated",
            "EXPLAIN SELECT 1",
            "   "
    })
    void rejectsEverythingElse(String sql) {
        assertThrows(QueryExceptions.Rejected.class, () -> SqlGuard.validate(sql));
    }

    @Test
    void extractsSqlFromMarkdownFences() {
        assertEquals("SELECT 1", SqlGuard.extractSql("```sql\nSELECT 1\n```"));
        assertEquals("SELECT 2", SqlGuard.extractSql("  SELECT 2  "));
    }
}