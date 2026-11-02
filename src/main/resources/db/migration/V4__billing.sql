-- Simulates a card that gets declined (dev/demo switch)
ALTER TABLE customers ADD COLUMN payment_method_valid BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE subscriptions ADD COLUMN billing_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE subscriptions ADD COLUMN next_billing_attempt_at TIMESTAMPTZ;

CREATE INDEX idx_subs_retry ON subscriptions (next_billing_attempt_at)
    WHERE next_billing_attempt_at IS NOT NULL;