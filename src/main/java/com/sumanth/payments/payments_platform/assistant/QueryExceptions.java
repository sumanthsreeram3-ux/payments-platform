package com.sumanth.payments.payments_platform.assistant;

public final class QueryExceptions {

    private QueryExceptions() {}

    /** The guard refused the SQL before it reached the database. */
    public static class Rejected extends RuntimeException {
        public Rejected(String message) { super(message); }
    }

    /** The database refused or failed the query (unknown column, permission denied, timeout...). */
    public static class Failed extends RuntimeException {
        public Failed(String message) { super(message); }
    }
}