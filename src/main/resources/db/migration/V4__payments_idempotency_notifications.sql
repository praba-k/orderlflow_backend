CREATE TABLE payments (
 id UUID PRIMARY KEY, order_id UUID NOT NULL UNIQUE REFERENCES orders(id), amount NUMERIC(19,2) NOT NULL CHECK(amount>=0),
 currency VARCHAR(3) NOT NULL, status VARCHAR(30) NOT NULL CHECK(status IN ('INITIATED','SUCCESS','FAILED','REFUND_PENDING','REFUNDED')),
 scenario VARCHAR(20) NOT NULL CHECK(scenario IN ('SUCCESS','FAILED','TIMEOUT')), attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL, last_error VARCHAR(100),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX payments_due_idx ON payments(next_attempt_at) WHERE status IN ('INITIATED','REFUND_PENDING');
CREATE TABLE mock_provider_ledger (
 payment_id UUID PRIMARY KEY, state VARCHAR(20) NOT NULL, amount NUMERIC(19,2) NOT NULL,
 currency VARCHAR(3) NOT NULL, charge_count INTEGER NOT NULL DEFAULT 0 CHECK(charge_count BETWEEN 0 AND 1)
);
CREATE TABLE idempotency_records (
 id UUID PRIMARY KEY, customer_id UUID NOT NULL REFERENCES customers(id), request_key VARCHAR(100) NOT NULL,
 request_fingerprint VARCHAR(64) NOT NULL, order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
 response_body TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL, UNIQUE(customer_id,request_key)
);
CREATE TABLE notifications (
 id UUID PRIMARY KEY, customer_id UUID NOT NULL REFERENCES customers(id), order_id UUID NOT NULL REFERENCES orders(id),
 event_type VARCHAR(40) NOT NULL, created_at TIMESTAMPTZ NOT NULL, read BOOLEAN NOT NULL DEFAULT false,
 UNIQUE(order_id,event_type)
);
CREATE INDEX notifications_customer_idx ON notifications(customer_id,created_at DESC);

