-- Inventory is the source of truth for stock.
-- available is DERIVED (on_hand - reserved), never stored: two sources of
-- truth is how overselling happens.

CREATE TABLE products (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   VARCHAR(64)  NOT NULL,
    sku         VARCHAR(64)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_tenant_sku UNIQUE (tenant_id, sku)
);

CREATE TABLE stock_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    VARCHAR(64) NOT NULL,
    product_id   UUID        NOT NULL REFERENCES products(id),
    warehouse_id VARCHAR(64) NOT NULL,
    on_hand      INTEGER     NOT NULL DEFAULT 0,
    reserved     INTEGER     NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_stock_tenant_product_wh UNIQUE (tenant_id, product_id, warehouse_id),
    CONSTRAINT ck_stock_on_hand_nonneg    CHECK (on_hand  >= 0),
    CONSTRAINT ck_stock_reserved_nonneg   CHECK (reserved >= 0),
    CONSTRAINT ck_stock_no_oversell       CHECK (on_hand  >= reserved)
);

-- allocation_ref is the caller's idempotency key (the reservation id).
-- The UNIQUE constraint is what makes a retried allocate safe.
CREATE TABLE allocations (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      VARCHAR(64)  NOT NULL,
    allocation_ref VARCHAR(128) NOT NULL,
    sku            VARCHAR(64)  NOT NULL,
    qty            INTEGER      NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    expires_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    released_at    TIMESTAMPTZ,
    committed_at   TIMESTAMPTZ,
    CONSTRAINT uq_alloc_tenant_ref UNIQUE (tenant_id, allocation_ref),
    CONSTRAINT ck_alloc_qty_positive CHECK (qty > 0),
    CONSTRAINT ck_alloc_status CHECK (status IN ('ALLOCATED','RELEASED','COMMITTED'))
);

-- Drives the leak sweeper: release ALLOCATED rows past expires_at.
CREATE INDEX idx_alloc_sweeper ON allocations (status, expires_at);

CREATE TABLE allocation_lines (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    allocation_id UUID        NOT NULL REFERENCES allocations(id) ON DELETE CASCADE,
    stock_item_id UUID        NOT NULL REFERENCES stock_items(id),
    warehouse_id  VARCHAR(64) NOT NULL,
    qty           INTEGER     NOT NULL,
    CONSTRAINT ck_alloc_line_qty_positive CHECK (qty > 0)
);

-- published_at is always NULL here. The column exists so the table is
-- outbox-shaped: adding a broker later means adding a relay, not a migration.
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
