package com.sumanth.payments.payments_platform.plan;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class Plan {
    @Id
    private UUID id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)   // column is CHAR(3); needed for ddl-auto=validate
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 10)
    private BillingInterval billingInterval;

    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Plan() {}

    public Plan(String name, long amountMinor, String currency, BillingInterval interval) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.billingInterval = interval;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public BillingInterval getBillingInterval() { return billingInterval; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}