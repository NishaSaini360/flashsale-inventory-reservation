# Manual verification

## Idempotency: same key twice → one reservation, reserved unchanged
## Expiry: reserve, wait past TTL, stock returns to full unaided
## Order: reserve → order → 6s → CONFIRMED, available unchanged across commit

Commands and outputs below.
