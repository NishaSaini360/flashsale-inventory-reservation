CREATE TABLE orders (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              VARCHAR(64) NOT NULL,
    reservation_id         UUID        NOT NULL,
    sku                    VARCHAR(64) NOT NULL,
    qty                    INTEGER     NOT NULL,
    status                 VARCHAR(32) NOT NULL,
    -- snapshot of the hold deadline. The TTL is never extended: it is a promise to
    -- other buyers. A payment landing after this goes down the compensation path.
    reservation_expires_at TIMESTAMPTZ,
    allocation_ref         VARCHAR(128),
    failure_reason         VARCHAR(255),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_order_qty CHECK (qty > 0),
    CONSTRAINT ck_order_status CHECK (status IN
        ('PENDING_PAYMENT','CONFIRMED','CANCELLED_PAYMENT_FAILED','CANCELLED_REFUNDED','CANCELLED_EXPIRED'))
);

CREATE INDEX idx_orders_reservation ON orders (tenant_id, reservation_id);
CREATE INDEX idx_orders_stuck ON orders (status, created_at);

CREATE TABLE payment_attempts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    VARCHAR(64)  NOT NULL,
    order_id     UUID         NOT NULL REFERENCES orders(id),
    external_ref VARCHAR(128) NOT NULL,
    amount_minor BIGINT       NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    settled_at   TIMESTAMPTZ,
    -- callback idempotency: the gateway may deliver the same callback twice
    CONSTRAINT uq_payment_external_ref UNIQUE (external_ref),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING','SUCCEEDED','FAILED','REFUNDED'))
);

CREATE TABLE domain_events (
    id             BIGSERIAL    PRIMARY KEY,
    tenant_id      VARCHAR(64)  NOT NULL,
    type           VARCHAR(64)  NOT NULL,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   VARCHAR(128) NOT NULL,
    payload        JSONB        NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_events_tenant_occurred ON domain_events (tenant_id, occurred_at);
