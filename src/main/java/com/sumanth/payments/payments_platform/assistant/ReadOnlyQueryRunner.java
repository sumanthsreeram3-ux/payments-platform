package com.sumanth.payments.payments_platform.assistant;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Component
public class ReadOnlyQueryRunner {

    public static final int MAX_ROWS = 200;
    private static final String TIMEOUT = "3s";

    public record QueryResult(List<String> columns, List<List<Object>> rows, boolean truncated) {}

    private final DataSource dataSource;

    public ReadOnlyQueryRunner(DataSource dataSource) { this.dataSource = dataSource; }

    /** Validates, then runs the query as the restricted role in a read-only transaction. */
    public QueryResult run(String rawSql) {
        String sql = SqlGuard.validate(rawSql);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            conn.setReadOnly(true);
            try (Statement st = conn.createStatement()) {
                // SET LOCAL lasts for this transaction only, so the pooled connection is clean afterwards.
                st.execute("SET LOCAL ROLE assistant_ro");
                st.execute("SET LOCAL statement_timeout = '" + TIMEOUT + "'");
                st.execute("SET LOCAL search_path = assistant");
                st.setMaxRows(MAX_ROWS + 1);

                try (ResultSet rs = st.executeQuery(sql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    List<String> columns = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(meta.getColumnLabel(i));
                    }
                    List<List<Object>> rows = new ArrayList<>();
                    boolean truncated = false;
                    while (rs.next()) {
                        if (rows.size() == MAX_ROWS) {
                            truncated = true;
                            break;
                        }
                        List<Object> row = new ArrayList<>(columnCount);
                        for (int i = 1; i <= columnCount; i++) {
                            row.add(normalize(rs.getObject(i)));
                        }
                        rows.add(row);
                    }
                    return new QueryResult(columns, rows, truncated);
                }
            } finally {
                conn.rollback();
            }
        } catch (SQLException e) {
            throw new QueryExceptions.Failed(firstLine(e.getMessage()));
        }
    }

    private static Object normalize(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant().toString();
        }
        return value.toString();
    }

    private static String firstLine(String message) {
        if (message == null) return "Query failed";
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}