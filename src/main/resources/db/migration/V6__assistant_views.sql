CREATE SCHEMA assistant;

CREATE VIEW assistant.customers AS
    SELECT id, email, name, payment_method_valid, created_at FROM public.customers;

CREATE VIEW assistant.plans AS
    SELECT id, name, amount_minor, currency, billing_interval, active, created_at FROM public.plans;

CREATE VIEW assistant.subscriptions AS
    SELECT id, customer_id, plan_id, status, current_period_start, current_period_end,
           canceled_at, billing_attempts, next_billing_attempt_at, created_at
    FROM public.subscriptions;

CREATE VIEW assistant.payments AS
    SELECT id, customer_id, subscription_id, amount_minor, currency, status, description, created_at
    FROM public.payments;

CREATE VIEW assistant.ledger_entries AS
    SELECT e.id, e.transaction_id, a.name AS account, a.type AS account_type, e.direction,
           e.amount_minor, e.currency, t.description, t.created_at
    FROM public.ledger_entries e
    JOIN public.accounts a ON a.id = e.account_id
    JOIN public.ledger_transactions t ON t.id = e.transaction_id;

-- Deliberately leaves out the payload and the endpoint secrets.
CREATE VIEW assistant.webhook_deliveries AS
    SELECT id, event_type, status, attempts, last_status_code, created_at, delivered_at
    FROM public.webhook_deliveries;

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'assistant_ro') THEN
        CREATE ROLE assistant_ro NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA assistant TO assistant_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA assistant TO assistant_ro;
GRANT assistant_ro TO CURRENT_USER;