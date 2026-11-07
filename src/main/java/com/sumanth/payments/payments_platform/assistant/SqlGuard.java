package com.sumanth.payments.payments_platform.assistant;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * First line of defence: accepts exactly one SELECT / WITH...SELECT statement.
 * The real boundary is the database role, so this is deliberately strict rather than clever.
 */
public final class SqlGuard {

    private static final int MAX_LENGTH = 4000;
    private static final Pattern WORD = Pattern.compile("[a-z_][a-z0-9_]*");
    private static final Pattern STARTS_WITH_SELECT = Pattern.compile("^(select|with)\\b");
    private static final Pattern FENCE = Pattern.compile("```(?:sql)?\\s*(.*?)```",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Set<String> FORBIDDEN = Set.of(
            "insert", "update", "delete", "merge", "drop", "alter", "create", "truncate", "grant", "revoke",
            "copy", "call", "do", "execute", "prepare", "deallocate", "set", "reset", "show", "vacuum",
            "analyze", "lock", "listen", "notify", "unlisten", "refresh", "reindex", "cluster", "comment",
            "into", "role", "authorization", "explain",
            "set_config", "current_setting", "dblink", "lo_import", "lo_export", "nextval", "setval",
            "currval", "query_to_xml", "table_to_xml", "cursor_to_xml", "schema_to_xml",
            "database_to_xml", "xmlparse");

    private SqlGuard() {}

    /** Pulls the SQL out of a model reply that may be wrapped in a markdown fence. */
    public static String extractSql(String modelOutput) {
        String text = modelOutput == null ? "" : modelOutput.trim();
        Matcher fence = FENCE.matcher(text);
        return fence.find() ? fence.group(1).trim() : text;
    }

    /** Returns the SQL to run (trailing semicolon removed) or throws Rejected with a reason. */
    public static String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new QueryExceptions.Rejected("The query is empty");
        }
        String sql = raw.trim();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        if (sql.length() > MAX_LENGTH) {
            throw new QueryExceptions.Rejected("The query is too long");
        }
        for (char c : sql.toCharArray()) {
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                throw new QueryExceptions.Rejected("Control characters are not allowed");
            }
        }
        // These change how Postgres reads string literals or identifiers, which would let a
        // statement hide from the scan below. None are needed for the views we expose.
        if (sql.contains("\\") || sql.contains("\"") || sql.contains("$")) {
            throw new QueryExceptions.Rejected(
                    "Double quotes, backslashes and dollar quoting are not allowed; use plain unquoted names");
        }

        String stripped = stripStringLiterals(sql);
        if (stripped.contains(";")) {
            throw new QueryExceptions.Rejected("Only one statement is allowed");
        }
        if (stripped.contains("--") || stripped.contains("/*")) {
            throw new QueryExceptions.Rejected("Comments are not allowed");
        }

        String lower = stripped.toLowerCase(Locale.ROOT);
        if (!STARTS_WITH_SELECT.matcher(lower).find()) {
            throw new QueryExceptions.Rejected("Only SELECT queries are allowed");
        }
        Matcher words = WORD.matcher(lower);
        while (words.find()) {
            String word = words.group();
            if (FORBIDDEN.contains(word) || word.startsWith("pg_") || word.equals("information_schema")) {
                throw new QueryExceptions.Rejected("Not allowed in a query: " + word);
            }
        }
        return sql;
    }

    /** Replaces every '...' literal with '' so keywords inside text values are not mistaken for SQL. */
    static String stripStringLiterals(String sql) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < sql.length()) {
            char ch = sql.charAt(i);
            if (ch != '\'') {
                out.append(ch);
                i++;
                continue;
            }
            int j = i + 1;
            boolean closed = false;
            while (j < sql.length()) {
                if (sql.charAt(j) == '\'') {
                    if (j + 1 < sql.length() && sql.charAt(j + 1) == '\'') {   // '' is an escaped quote
                        j += 2;
                        continue;
                    }
                    closed = true;
                    break;
                }
                j++;
            }
            if (!closed) {
                throw new QueryExceptions.Rejected("Unterminated string literal");
            }
            out.append("''");
            i = j + 1;
        }
        return out.toString();
    }
}