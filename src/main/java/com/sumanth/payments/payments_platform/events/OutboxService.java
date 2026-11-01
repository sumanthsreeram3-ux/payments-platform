package com.sumanth.payments.payments_platform.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboxService {

    private final JdbcTemplate jdbc;

    public OutboxService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // MANDATORY: the event must join the caller's transaction, never run on its own.
    @Transactional(propagation = Propagation.MANDATORY)
    public void add(String aggregateType, UUID aggregateId, String eventType, String payloadJson) {
        jdbc.update("""
                INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload)
                VALUES (?, ?, ?, ?, ?::jsonb)
                """,
                UUID.randomUUID(), aggregateType, aggregateId, eventType, payloadJson);
    }
}