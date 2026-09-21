# ADR-001: Concurrency control for stock allocation

**Status:** Accepted

## Context

Flash sales concentrate many concurrent allocations on a single hot SKU row.
Overselling is unacceptable. inventory-service is the sole owner of stock.

## Decision

Allocation is a **single conditional UPDATE** whose predicate re-checks availability:

    UPDATE stock_items
       SET reserved = reserved + :qty
     WHERE id = :id AND tenant_id = :tenantId
       AND on_hand - reserved >= :qty;

rowsAffected == 0 means insufficient stock and maps to 409. There is no read
before the write and no application-level lock.

available is always derived as on_hand - reserved; it is never stored. Two
sources of truth is how overselling happens.

## Why this works

Under READ COMMITTED, Postgres re-evaluates the WHERE clause against the newly
committed row version after acquiring the row lock (EvalPlanQual). A concurrent
writer that already consumed stock therefore makes this predicate fail rather
than letting this update overwrite theirs. A plain SELECT followed by an UPDATE
gets no such protection and loses updates.

The row is the lock stripe: contention partitions naturally per
(tenant, sku, warehouse), with no separate lock table.

## Alternatives considered

| Option | Verdict |
|---|---|
| SELECT then UPDATE, no lock | Broken under READ COMMITTED - lost update |
| SELECT ... FOR UPDATE then UPDATE | Correct, two round trips. Used where one allocation spans several rows; rows locked in deterministic warehouse_id order to avoid deadlock |
| @Version optimistic + retry | Correct but wrong shape - one hot row becomes a retry storm |
| Redis / distributed lock | Adds a second source of truth and a fencing problem; the row is already a lock |
| synchronized | Disqualified by the brief, and useless across replicas |

## Backstops

Database CHECK constraints make the invariant structural:

    CHECK (on_hand >= 0), CHECK (reserved >= 0), CHECK (on_hand >= reserved)

If any code path is ever wrong, the transaction dies with a constraint violation
instead of silently overselling. A 500 in the logs beats corrupt stock.

## Guarded state transitions

Release and commit are conditional on current status:

    UPDATE allocations SET status='RELEASED' WHERE id=? AND status='ALLOCATED';

Only the caller that wins this update may touch stock_items. Double-release is
the sneakiest oversell path - two releases would decrement reserved twice and
inflate availability. DoubleReleaseTest fires eight concurrent releases and
asserts reserved drops exactly once.

## Effect on the invariant

- allocate: reserved += q, available drops
- release:  reserved -= q, available restored
- commit:   on_hand -= q and reserved -= q, **available unchanged** (held stock becomes sold stock)

## Consequences

Throughput per SKU is bounded by row-lock duration, which is a single short
statement with no network call inside the transaction. Acceptable: correctness
under contention is the requirement, not maximum per-row throughput.
