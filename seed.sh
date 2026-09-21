#!/usr/bin/env bash
set -euo pipefail

INV=${INV:-http://localhost:8081}
RES=${RES:-http://localhost:8082}
ORD=${ORD:-http://localhost:8083}
TENANT_A=${TENANT_A:-tenant-a}
TENANT_B=${TENANT_B:-tenant-b}

echo "Waiting for services..."
for url in "$INV" "$RES" "$ORD"; do
  for i in $(seq 1 60); do
    if curl -fsS "$url/actuator/health" >/dev/null 2>&1; then break; fi
    sleep 2
    if [ "$i" = 60 ]; then echo "TIMEOUT: $url never became healthy"; exit 1; fi
  done
done
echo "All three services healthy."
echo

ADMIN_A=$(curl -fsS "$INV/actuator/devtoken?tenant=$TENANT_A&role=ADMIN")
USER_A_INV=$(curl -fsS "$INV/actuator/devtoken?tenant=$TENANT_A&role=USER")
USER_A_RES=$(curl -fsS "$RES/actuator/devtoken?tenant=$TENANT_A&role=USER")
USER_A_ORD=$(curl -fsS "$ORD/actuator/devtoken?tenant=$TENANT_A&role=USER")
USER_B_INV=$(curl -fsS "$INV/actuator/devtoken?tenant=$TENANT_B&role=USER")
ADMIN_B=$(curl -fsS "$INV/actuator/devtoken?tenant=$TENANT_B&role=ADMIN")

echo "Seeding $TENANT_A ..."
curl -fsS -X POST "$INV/api/v1/admin/products" -H "Authorization: Bearer $ADMIN_A" \
  -H 'Content-Type: application/json' -d '{"sku":"FLASH-1","name":"Flash Sale Widget"}' >/dev/null || true
curl -fsS -X POST "$INV/api/v1/admin/stock" -H "Authorization: Bearer $ADMIN_A" \
  -H 'Content-Type: application/json' -d '{"sku":"FLASH-1","warehouseId":"wh-1","qty":100}' >/dev/null

# Same SKU string under a different tenant — proves isolation is real.
echo "Seeding $TENANT_B with the SAME sku ..."
curl -fsS -X POST "$INV/api/v1/admin/products" -H "Authorization: Bearer $ADMIN_B" \
  -H 'Content-Type: application/json' -d '{"sku":"FLASH-1","name":"Tenant B Widget"}' >/dev/null || true
curl -fsS -X POST "$INV/api/v1/admin/stock" -H "Authorization: Bearer $ADMIN_B" \
  -H 'Content-Type: application/json' -d '{"sku":"FLASH-1","warehouseId":"wh-1","qty":50}' >/dev/null

cat <<TOKENS

Seed complete. FLASH-1: 100 units for $TENANT_A, 50 for $TENANT_B.

Export these:

export ADMIN_A=$ADMIN_A
export USER_A_INV=$USER_A_INV
export USER_A_RES=$USER_A_RES
export USER_A_ORD=$USER_A_ORD
export USER_B_INV=$USER_B_INV

Try:

  curl -s $INV/api/v1/stock/FLASH-1 -H "Authorization: Bearer \$USER_A_INV"
  curl -s $INV/api/v1/stock/FLASH-1 -H "Authorization: Bearer \$USER_B_INV"   # different numbers
  curl -s -X POST $RES/api/v1/reservations -H "Authorization: Bearer \$USER_A_RES" \\
       -H "Idempotency-Key: demo-1" -H 'Content-Type: application/json' \\
       -d '{"sku":"FLASH-1","qty":10}'

TOKENS
