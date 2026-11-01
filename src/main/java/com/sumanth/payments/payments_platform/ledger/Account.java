package com.sumanth.payments.payments_platform.ledger;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountType type;

    protected Account() {}

    public UUID getId() { return id; }
    public String getName() { return name; }
    public AccountType getType() { return type; }
}