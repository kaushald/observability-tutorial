#!/usr/bin/env bash
# Starts a lesson with spans printed to the console, places two orders, and checks
# that the traces look the way the lesson promises. No Honeycomb key needed.
# Usage: scripts/smoke.sh <lesson-dir>, for example scripts/smoke.sh 001-auto
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LESSON="${1:?usage: smoke.sh <lesson-dir>}"
LOG="$(mktemp)"
BODY="$(mktemp)"
# Lesson number decides which checks apply: later lessons keep every earlier change
lesson_number=$((10#${LESSON%%-*}))

export OTEL_TRACES_EXPORTER=console
BASE="http://localhost:${ORDER_PORT:-8080}"

"$ROOT/$LESSON/run.sh" > "$LOG" 2>&1 &
RUNNER=$!
stop_lesson() {
  kill "$RUNNER" 2>/dev/null || true
  wait "$RUNNER" 2>/dev/null || true
}
trap stop_lesson EXIT

fail() {
  echo "FAIL: $1" >&2
  echo "--- last 40 log lines ---" >&2
  tail -40 "$LOG" | cut -c1-220 >&2
  exit 1
}

for _ in $(seq 1 180); do
  grep -q "^Ready in" "$LOG" && break
  kill -0 "$RUNNER" 2>/dev/null || fail "lesson exited before it was ready"
  sleep 0.5
done
grep -q "^Ready in" "$LOG" || fail "lesson was not ready within 90 seconds"
grep "^Ready in" "$LOG"

# order <restaurantId> <itemId> [cardNumber]
order() {
  curl -s -o "$BODY" -w '%{http_code}' -X POST "$BASE/api/orders" \
    -H 'Content-Type: application/json' \
    -d "{\"restaurantId\":$1,\"itemIds\":[$2],\"cardNumber\":\"${3:-4242 4242 4242 4242}\"}"
}

first_item() {
  # awk reads the whole stream: closing a pipe early would trip pipefail
  curl -sf "$BASE/api/restaurants/$1/menu" | grep -o '"id":[0-9]*' | awk -F: 'NR == 1 { print $2 }'
}

code="$(order 1 "$(first_item 1)")"
[ "$code" = "201" ] || fail "order from restaurant 1 returned $code, expected 201"
echo "ok: order from Bella Pizza confirmed (201)"

code="$(order 1 "$(first_item 1)" "4000 0000 0000 0000")"
[ "$code" = "402" ] || fail "order with a card ending 0000 returned $code, expected 402"
echo "ok: card ending 0000 declined (402)"

code="$(order 2 "$(first_item 2)")"
[ "$code" = "504" ] || fail "order from restaurant 2 returned $code, expected 504"
echo "ok: order from Slow Noodles failed (504)"

if [ "$lesson_number" -ge 5 ]; then
  failed_order="$(grep -o '"orderId":[0-9]*' "$BODY" | awk -F: 'NR == 1 { print $2 }')"
  code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/orders/$failed_order/refund")"
  [ "$code" = "200" ] || fail "refund of failed order $failed_order returned $code, expected 200"
  code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/orders/$failed_order/refund")"
  [ "$code" = "409" ] || fail "second refund of order $failed_order returned $code, expected 409"
  echo "ok: failed order $failed_order refunded once (200, then 409)"
fi

# Stop the lesson so every service flushes its spans
stop_lesson
trap - EXIT

if [ "$LESSON" = "000-baseline" ]; then
  if grep -qi 'traceid' "$LOG"; then
    fail "baseline lesson printed spans, but tracing should be off"
  fi
  echo "ok: no spans in the baseline lesson"
else
  # The Java console exporter prints: 'POST /api/orders' : <traceId> <spanId> SERVER ...
  trace_id="$(grep -o "'POST /api/orders' : [0-9a-f]\{32\}" "$LOG" | awk 'NR == 1 { print $NF }')"
  [ -n "$trace_id" ] || fail "no 'POST /api/orders' server span from the order service"
  echo "ok: order service span found, trace $trace_id"
  grep -Eq "^\[kitchen\].*$trace_id" "$LOG" || fail "kitchen-service has no span in trace $trace_id"
  echo "ok: kitchen-service span is in the same trace"
  grep -Eq "^\[delivery\].*$trace_id" "$LOG" || fail "delivery-service has no span in trace $trace_id"
  echo "ok: delivery-service span is in the same trace"
  grep -Eqi "^\[orders\].*'SELECT " "$LOG" || fail "no database spans from the order service"
  echo "ok: database spans present"
  notification="$(grep -m1 -E '^\[delivery\].*"Name":"POST /notifications"' "$LOG" || true)"
  [ -n "$notification" ] || fail "delivery-service has no POST /notifications span"
  if [ "$lesson_number" -lt 3 ]; then
    # The confirmation runs on a raw thread, so until lesson 003 fixes it, its span
    # must be in a different trace from the order
    case "$notification" in
      *"$trace_id"*) fail "the confirmation span is inside the order trace; it should be orphaned in this lesson" ;;
    esac
    echo "ok: confirmation span is orphaned from the order trace"
  else
    case "$notification" in
      *"$trace_id"*) echo "ok: confirmation span is inside the order trace" ;;
      *) fail "the confirmation span is not in the order trace; lesson 003 should have fixed that" ;;
    esac
  fi

  if [ "$lesson_number" -ge 2 ]; then
    for span in validate-order calculate-price authorize-payment fulfil-order; do
      grep -Eq "^\[orders\].*'$span' : $trace_id" "$LOG" || fail "no '$span' span in the order trace"
    done
    echo "ok: manual spans present (validate-order, calculate-price, authorize-payment, fulfil-order)"
    server_span="$(grep -m1 -E "^\[orders\].*'POST /api/orders' : $trace_id" "$LOG" || true)"
    for attribute in "order.id=" "restaurant.id=1" "order.total_cents=" "order.item_count=1" "payment.declined=false"; do
      case "$server_span" in
        *"$attribute"*) ;;
        *) fail "the order's server span is missing attribute $attribute" ;;
      esac
    done
    echo "ok: order attributes are on the server span"
    grep -Eq "^\[orders\].*'POST /api/orders' : .*payment.declined=true" "$LOG" || fail "no span with payment.declined=true for the declined card"
    echo "ok: declined card recorded as an attribute"
  fi

  # The menu loads tags one item at a time until lesson 006 fixes it. This script opens
  # three menus of 8 items (once per order it places), so the planted N+1 shows up as
  # 24 tag queries.
  tag_queries="$(grep -c "'SELECT orderdb.menu_item_tags\?'" "$LOG" || true)"
  if [ "$lesson_number" -lt 6 ]; then
    [ "$tag_queries" -ge 24 ] || fail "expected the planted N+1 (24 tag queries), found $tag_queries"
    echo "ok: planted N+1 is present ($tag_queries tag queries for three menus)"
  else
    [ "$tag_queries" -eq 0 ] || fail "lesson 006 should load tags with the items, found $tag_queries separate tag queries"
    echo "ok: N+1 is fixed (no separate tag queries)"
  fi

  if [ "$lesson_number" -ge 5 ]; then
    refund_trace="$(grep -o "'refund-order' : [0-9a-f]\{32\}" "$LOG" | awk 'NR == 1 { print $NF }')"
    [ -n "$refund_trace" ] || fail "no 'refund-order' span from the order service"
    grep -Eq "^\[orders\].*'refund-payment' : $refund_trace" "$LOG" || fail "no 'refund-payment' span in the refund trace"
    # A refund is linked to the order, not a child of it: it must be in a trace of its own
    if grep -Eq "'POST /api/orders' : $refund_trace" "$LOG"; then
      fail "the refund is inside an order's trace; it should be a separate, linked trace"
    fi
    echo "ok: refund has its own trace with refund-order and refund-payment spans"
  fi
fi

rm -f "$LOG" "$BODY"
echo "PASS: $LESSON"
