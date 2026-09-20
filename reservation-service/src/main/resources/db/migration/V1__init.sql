CREATE TABLE reservations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64)  NOT NULL,
    sku             VARCHAR(64)  NOT NULL,
    qty             INTEGER      NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    expires_at      TIMESTAMPTZ,
    allocation_ref  VARCHAR(128) NOT NULL,
    release_state   VARCHAR(16)  NOT NULL DEFAULT 'NOT_NEEDED',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_res_qty CHECK (qty > 0),
    CONSTRAINT ck_res_status CHECK (status IN ('PENDING','ACTIVE','EXPIRED','CONFIRMED','CANCELLED','FAILED')),
    CONSTRAINT ck_res_release_state CHECK (release_state IN ('NOT_NEEDED','PENDING','DONE'))
);

-- Drives the expiry worker's claim query.
CREATE INDEX idx_res_expiry ON reservations (status, expires_at);
-- Drives the retry sweep for releases that failed.
CREATE INDEX idx_res_release_retry ON reservations (status, release_state);
-- Recovers reservations orphaned by a crash between allocate and ACTIVE.
CREATE INDEX idx_res_pending ON reservations (status, created_at);

CREATE TABLE idempotency_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64)  NOT NULL,
    idem_key        VARCHAR(255) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    state           VARCHAR(16)  NOT NULL,
    reservation_id  UUID,
    response_status INTEGER,
    response_body   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idem_tenant_key UNIQUE (tenant_id, idem_key),
    CONSTRAINT ck_idem_state CHECK (state IN ('IN_PROGRESS','COMPLETED','FAILED'))
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
