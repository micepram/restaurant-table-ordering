#!/usr/bin/env bash
#
# End-to-end walkthrough of the whole system, entirely through the gateway on :8080.
#
#   ./scripts/demo.sh
#
# Assumes the backend is up (./scripts/dev.sh start). Resets the table it uses first, so
# it produces the same output on a second run.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

GW="${RTO_GATEWAY:-localhost:8080}"
QR="qr-t03-7b5f0a"
HELPERS="$REPO_ROOT/scripts/demo_helpers.py"

field() { python3 "$HELPERS" field "$1"; }
step()  { printf '\n\033[1m── %s\033[0m\n' "$1"; }

if ! curl -sf "http://$GW/actuator/health" >/dev/null 2>&1 \
  && ! curl -s -o /dev/null "http://$GW/api/menu"; then
  echo "Gateway is not answering on $GW. Run ./scripts/dev.sh start first." >&2
  exit 1
fi

step "1. staff sign in"
KT=$(curl -s -X POST "$GW/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"marco","password":"marco-pw"}' | field token)
AT=$(curl -s -X POST "$GW/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"ana","password":"ana-pw"}' | field token)
[ -n "$KT" ] && echo "   marco (KITCHEN) and ana (STAFF) signed in" || { echo "   login failed"; exit 1; }

step "2. diner scans the QR code at the table"
SESSION=$(curl -s -X POST "$GW/api/tables/sessions" -H 'Content-Type: application/json' -d "{\"qrCode\":\"$QR\"}")
CT=$(echo "$SESSION" | field token)
TID=$(echo "$SESSION" | field tableId)
echo "   session opened for table $(echo "$SESSION" | field tableCode) (id $TID)"

# Start from a known state so a second run reads the same as the first.
curl -s -o /dev/null -X POST "$GW/api/tables/$TID/state" -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' -d '{"state":"SEATED"}'
echo "   table reset to $(curl -s "$GW/api/tables/$TID" -H "Authorization: Bearer $AT" | field state)"

step "3. diner orders salmon + 2 steaks + chips"
ORDER=$(curl -s -X POST "$GW/api/orders" -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' -d '{
  "lines":[{"menuItemId":4,"quantity":1,"modifierIds":[2],"note":"no capers"},
           {"menuItemId":5,"quantity":2,"modifierIds":[7,4]},
           {"menuItemId":8,"quantity":1}]}')
OID=$(echo "$ORDER" | field id)
echo "   order #$OID placed, subtotal $(echo "$ORDER" | python3 "$HELPERS" money subtotalCents)"
sleep 3

step "4. the order event advanced the table state"
echo "   state = $(curl -s "$GW/api/tables/$TID" -H "Authorization: Bearer $AT" | field state)  (was SEATED)"

step "5. the ticket fanned in to the kitchen board"
curl -s "$GW/api/kitchen/board" -H "Authorization: Bearer $KT" | python3 "$HELPERS" ticket "$OID"

step "6. kitchen runs the ticket through to READY"
for status in ACKNOWLEDGED PREPARING READY; do
  curl -s -o /dev/null -X POST "$GW/api/kitchen/tickets/$OID/advance" -H "Authorization: Bearer $KT" \
    -H 'Content-Type: application/json' -d "{\"status\":\"$status\"}"
  sleep 2
done
echo "   order status = $(curl -s "$GW/api/orders/$OID" -H "Authorization: Bearer $CT" | field status)"

step "7. kitchen 86s the salmon — the availability flow"
printf '   redis before: %s\n' "$(docker compose exec -T redis redis-cli KEYS 'menu*' | tr -d '\r\n')"
curl -s -o /dev/null -X POST "$GW/api/kitchen/items/4/availability" -H "Authorization: Bearer $KT" \
  -H 'Content-Type: application/json' -d '{"available":false,"reason":"sold out"}'
sleep 4
AFTER=$(docker compose exec -T redis redis-cli KEYS 'menu*' | tr -d '\r\n')
printf '   redis after:  %s\n' "${AFTER:-(empty — cache evicted)}"
docker compose exec -T postgres psql -U rto -d rto -tAc \
  "SELECT '   db: '||name||' available='||available FROM menu.menu_item WHERE id=4"

step "8. the 86'd item can no longer be ordered"
curl -s -X POST "$GW/api/orders" -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' \
  -d '{"lines":[{"menuItemId":4,"quantity":1}]}' | python3 "$HELPERS" problem

step "9. bill — 18% tip, split three ways"
curl -s -o /dev/null -X POST "$GW/api/payments/bills/$OID" -H "Authorization: Bearer $CT"
curl -s -X POST "$GW/api/payments/bills/$OID/tip" -H "Authorization: Bearer $CT" \
  -H 'Content-Type: application/json' -d '{"percent":18}' | python3 "$HELPERS" bill_totals
SPLIT=$(curl -s "$GW/api/payments/bills/$OID/split?ways=3" -H "Authorization: Bearer $CT")
echo "$SPLIT" | python3 "$HELPERS" split

step "10. three payers settle, one card declining first"
pay() {
  curl -s -o /dev/null -X POST "$GW/api/payments/bills/$OID/pay" -H "Authorization: Bearer $CT" \
    -H 'Content-Type: application/json' -d "{\"amountCents\":$1,\"tipCents\":$2,\"cardNumber\":\"$3\"}"
}
read -r A1 T1 A2 T2 A3 T3 <<<"$(echo "$SPLIT" | python3 "$HELPERS" shares)"
pay "$A1" "$T1" 4111111111111111
pay "$A2" "$T2" 4111111111110000   # forced decline
pay "$A2" "$T2" 5555444433332222   # retry on another card
echo "   after 2 of 3 paid, order = $(curl -s "$GW/api/orders/$OID" -H "Authorization: Bearer $CT" | field status)"
pay "$A3" "$T3" 340000000000009
sleep 3
echo "   after all 3 paid,  order = $(curl -s "$GW/api/orders/$OID" -H "Authorization: Bearer $CT" | field status)"
curl -s "$GW/api/payments/bills/$OID" -H "Authorization: Bearer $CT" | python3 "$HELPERS" bill

step "11. table settles, and staff can flag it for attention"
echo "   state = $(curl -s "$GW/api/tables/$TID" -H "Authorization: Bearer $AT" | field state)"
curl -s -X POST "$GW/api/tables/$TID/attention" -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' -d '{"flagged":true,"note":"needs clearing"}' \
  | python3 "$HELPERS" attention

step "12. restore the salmon so the demo can be re-run"
curl -s -o /dev/null -X POST "$GW/api/kitchen/items/4/availability" -H "Authorization: Bearer $KT" \
  -H 'Content-Type: application/json' -d '{"available":true,"reason":"restocked"}'
curl -s -o /dev/null -X POST "$GW/api/tables/$TID/attention" -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' -d '{"flagged":false}'
sleep 2
curl -s "$GW/api/menu" -H "Authorization: Bearer $CT" | python3 "$HELPERS" item 4

printf '\n\033[1m── demo complete\033[0m\n'
