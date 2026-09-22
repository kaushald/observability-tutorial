#!/usr/bin/env bash
# Starts the three services for a lesson and stops them all on Ctrl+C.
# Usage: scripts/start-lesson.sh <lesson-dir> [--no-tracing]
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -d /usr/local/go/bin ] && PATH="$PATH:/usr/local/go/bin"

LESSON="${1:?usage: start-lesson.sh <lesson-dir> [--no-tracing]}"
TRACING="on"
if [ "${2:-}" = "--no-tracing" ]; then
  TRACING="off"
fi
export TRACING

. "$ROOT/scripts/env.sh"

SERVICE_DIR="$ROOT/$LESSON/order-service"
JAR="$SERVICE_DIR/build/libs/order-service.jar"
GRADLE_PROJECT=":lesson-${LESSON%%-*}"
RUN_DIR="$ROOT/.run"

# Refuse to start on top of a previous run
for port in "$ORDER_PORT" "$KITCHEN_PORT" "$DELIVERY_PORT"; do
  if command -v lsof >/dev/null && lsof -ti "tcp:$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Error: port $port is in use. Run advanced/scripts/stop.sh first." >&2
    exit 1
  fi
done

# Rebuild only when sources are newer than the artifact
if [ ! -f "$JAR" ] || [ -n "$(find "$SERVICE_DIR/src" "$SERVICE_DIR/build.gradle" -newer "$JAR" -print -quit)" ]; then
  echo "Building $LESSON order service..."
  (cd "$ROOT" && ./gradlew --quiet "$GRADLE_PROJECT:bootJar")
fi
KITCHEN="$ROOT/services/bin/kitchen-service"
DELIVERY="$ROOT/services/bin/delivery-service"
if [ ! -x "$KITCHEN" ] || [ ! -x "$DELIVERY" ] || [ -n "$(find "$ROOT/services" -name '*.go' -newer "$KITCHEN" -print -quit)" ]; then
  echo "Building Go services..."
  (cd "$ROOT/services" && go build -o bin/ ./kitchen-service ./delivery-service)
fi

PIDS=()

# Prefixes each line of stdin with the service name. A bash loop, not awk: mawk (the awk
# on Debian, so in Codespaces) block-buffers input from a pipe, which held every log
# line back until the service exited.
prefix() {
  local line
  while IFS= read -r line || [ -n "$line" ]; do
    printf '%s%s\n' "$1" "$line"
  done
}

start() {
  local name="$1"
  shift
  "$@" > >(prefix "[$name] ") 2>&1 &
  PIDS+=($!)
}

cleanup() {
  trap - INT TERM EXIT
  echo
  echo "Stopping services..."
  if [ "${#PIDS[@]}" -gt 0 ]; then
    kill "${PIDS[@]}" 2>/dev/null || true
  fi
  wait 2>/dev/null || true
  rm -f "$RUN_DIR/pids"
}
trap cleanup INT TERM EXIT

START=$SECONDS
export SERVER_PORT="$ORDER_PORT"
export KITCHEN_URL="http://localhost:$KITCHEN_PORT"
export DELIVERY_URL="http://localhost:$DELIVERY_PORT"
start kitchen env OTEL_SERVICE_NAME=kitchen-service PORT="$KITCHEN_PORT" "$KITCHEN"
start delivery env OTEL_SERVICE_NAME=delivery-service PORT="$DELIVERY_PORT" "$DELIVERY"
if [ "$TRACING" = "on" ]; then
  # shellcheck disable=SC2086
  start orders env OTEL_SERVICE_NAME=order-service java $JAVA_OPTS "-javaagent:$JAVA_AGENT" -jar "$JAR"
else
  # shellcheck disable=SC2086
  start orders java $JAVA_OPTS -jar "$JAR"
fi

mkdir -p "$RUN_DIR"
printf '%s\n' "${PIDS[@]}" > "$RUN_DIR/pids"

# Wait until the order service answers
ready=""
for _ in $(seq 1 120); do
  if curl -sf -o /dev/null http://localhost:$ORDER_PORT/api/restaurants; then
    ready="yes"
    break
  fi
  sleep 0.5
done
if [ -z "$ready" ]; then
  echo "Error: the order service did not start within 60 seconds." >&2
  exit 1
fi

URL="http://localhost:$ORDER_PORT"
if [ -n "${CODESPACE_NAME:-}" ]; then
  URL="https://${CODESPACE_NAME}-${ORDER_PORT}.${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-app.github.dev}"
fi
echo
echo "Ready in $((SECONDS - START))s (tracing: $TRACING). Open $URL"
echo "Press Ctrl+C to stop."
echo

wait
