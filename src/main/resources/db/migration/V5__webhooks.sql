CREATE TABLE webhook_endpoints (
    id          UUID PRIMARY KEY,
    url         VARCHAR(2048) NOT NULL,
    secret      VARCHAR(100)  NOT NULL,
    event_types VARCHAR(500),                  -- comma-separated; NULL means all events
    active      BOOLEAN       NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE webhook_deliveries (
    id               UUID PRIMARY KEY,
    endpoint_id      UUID         NOT NULL REFERENCES webhook_endpoints(id),
    event_id         UUID         NOT NULL,
    event_type       VARCHAR(100) NOT NULL,
    payload          JSONB        NOT NULL,
    status           VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts         INT          NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ,
    last_status_code INT,
    last_error       VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    delivered_at     TIMESTAMPTZ,
    UNIQUE (endpoint_id, event_id)
);

CREATE INDEX idx_webhook_due ON webhook_deliveries (next_attempt_at) WHERE status = 'PENDING';