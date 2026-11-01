CREATE TABLE payments (
    id                    UUID PRIMARY KEY,
    customer_id           UUID NOT NULL REFERENCES customers(id),
    subscription_id       UUID REFERENCES subscriptions(id),
    amount_minor          BIGINT NOT NULL CHECK (amount_minor > 0),
    currency              CHAR(3) NOT NULL,
    status                VARCHAR(10) NOT NULL CHECK (status IN ('SUCCEEDED','FAILED')),
    description           VARCHAR(255),
    ledger_transaction_id UUID REFERENCES ledger_transactions(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE idempotency_keys ADD COLUMN payment_id UUID REFERENCES payments(id);

INSERT INTO accounts (id, name, type) VALUES
    (gen_random_uuid(), 'CASH', 'ASSET'),
    (gen_random_uuid(), 'REVENUE', 'REVENUE');