#!/usr/bin/env bash
# Starts a lesson with spans printed to the console, places two orders, and checks
# that the traces look the way the lesson promises. No Honeycomb key needed.
# Usage: scripts/smoke.sh <000-baseline|001-auto>
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LESSON="${1:?usage: smoke.sh <lesson-dir>}"
LOG="$(mktemp)"

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
  curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/orders" \
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
  # The confirmation runs on a raw thread, so until lesson 003 fixes it, its span
  # must be in a different trace from the order
  notification="$(grep -m1 -E '^\[delivery\].*"Name":"POST /notifications"' "$LOG" || true)"
  [ -n "$notification" ] || fail "delivery-service has no POST /notifications span"
  case "$notification" in
    *"$trace_id"*) fail "the confirmation span is inside the order trace; it should be orphaned in this lesson" ;;
  esac
  echo "ok: confirmation span is orphaned from the order trace"
fi

rm -f "$LOG"
echo "PASS: $LESSON"
