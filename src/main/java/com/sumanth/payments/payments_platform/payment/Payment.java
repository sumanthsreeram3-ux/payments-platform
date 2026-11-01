package com.sumanth.payments.payments_platform.payment;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    private UUID id;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "subscription_id")
    private UUID subscriptionId;
    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentStatus status;
    @Column(length = 255)
    private String description;
    @Column(name = "ledger_transaction_id")
    private UUID ledgerTransactionId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Payment() {}

    public Payment(UUID customerId, UUID subscriptionId, long amountMinor, String currency,
                   PaymentStatus status, String description, UUID ledgerTransactionId) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.subscriptionId = subscriptionId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = status;
        this.description = description;
        this.ledgerTransactionId = ledgerTransactionId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}