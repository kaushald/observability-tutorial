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
  tail -40 "$LOG" >&2
  exit 1
}

for _ in $(seq 1 180); do
  grep -q "^Ready in" "$LOG" && break
  kill -0 "$RUNNER" 2>/dev/null || fail "lesson exited before it was ready"
  sleep 0.5
done
grep -q "^Ready in" "$LOG" || fail "lesson was not ready within 90 seconds"
grep "^Ready in" "$LOG"

order() {
  curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/orders" \
    -H 'Content-Type: application/json' -d "{\"restaurantId\":$1,\"itemIds\":[$2]}"
}

first_item() {
  curl -sf "$BASE/api/restaurants/$1/menu" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2
}

code="$(order 1 "$(first_item 1)")"
[ "$code" = "201" ] || fail "order from restaurant 1 returned $code, expected 201"
echo "ok: order from Bella Pizza confirmed (201)"

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
  trace_id="$(grep -o "'POST /api/orders' : [0-9a-f]\{32\}" "$LOG" | head -1 | awk '{ print $NF }')"
  [ -n "$trace_id" ] || fail "no 'POST /api/orders' server span from the order service"
  echo "ok: order service span found, trace $trace_id"
  grep '^\[kitchen\]' "$LOG" | grep -q "$trace_id" || fail "kitchen-service has no span in trace $trace_id"
  echo "ok: kitchen-service span is in the same trace"
  grep '^\[delivery\]' "$LOG" | grep -q "$trace_id" || fail "delivery-service has no span in trace $trace_id"
  echo "ok: delivery-service span is in the same trace"
  grep '^\[orders\]' "$LOG" | grep -qi "select" || fail "no database spans from the order service"
  echo "ok: database spans present"
fi

rm -f "$LOG"
echo "PASS: $LESSON"
