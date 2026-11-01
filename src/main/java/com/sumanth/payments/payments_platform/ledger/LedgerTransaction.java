package com.sumanth.payments.payments_platform.ledger;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String description;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerTransaction() {}

    public LedgerTransaction(String description) {
        this.id = UUID.randomUUID();
        this.description = description;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}