CREATE TABLE customers (
    id          UUID PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE plans (
    id              UUID PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    amount_minor    BIGINT NOT NULL CHECK (amount_minor > 0),  -- paise/cents, never floats
    currency        CHAR(3) NOT NULL,
    billing_interval VARCHAR(10) NOT NULL CHECK (billing_interval IN ('MONTHLY','YEARLY')),
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE subscriptions (
    id                   UUID PRIMARY KEY,
    customer_id          UUID NOT NULL REFERENCES customers(id),
    plan_id              UUID NOT NULL REFERENCES plans(id),
    status               VARCHAR(20) NOT NULL
        CHECK (status IN ('TRIALING','ACTIVE','PAST_DUE','CANCELED')),
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end   TIMESTAMPTZ NOT NULL,
    canceled_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_subs_due ON subscriptions (status, current_period_end);

CREATE TABLE idempotency_keys (
    key            VARCHAR(255) PRIMARY KEY,
    request_hash   VARCHAR(64) NOT NULL,
    response_body  JSONB,
    status_code    INT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,   -- e.g. 'CUSTOMER_RECEIVABLE', 'REVENUE'
    type        VARCHAR(10) NOT NULL CHECK (type IN ('ASSET','LIABILITY','REVENUE','EXPENSE'))
);

CREATE TABLE ledger_transactions (
    id          UUID PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY,
    transaction_id  UUID NOT NULL REFERENCES ledger_transactions(id),
    account_id      UUID NOT NULL REFERENCES accounts(id),
    direction       VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
    amount_minor    BIGINT NOT NULL CHECK (amount_minor > 0),
    currency        CHAR(3) NOT NULL
);