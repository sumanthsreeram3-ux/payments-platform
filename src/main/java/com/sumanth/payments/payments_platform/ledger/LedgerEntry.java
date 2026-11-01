package com.sumanth.payments.payments_platform.ledger;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id
    private UUID id;
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Direction direction;
    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    protected LedgerEntry() {}

    public LedgerEntry(UUID transactionId, UUID accountId, Direction direction,
                       long amountMinor, String currency) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.direction = direction;
        this.amountMinor = amountMinor;
        this.currency = currency;
    }

    public UUID getId() { return id; }
    public Direction getDirection() { return direction; }
    public long getAmountMinor() { return amountMinor; }
}