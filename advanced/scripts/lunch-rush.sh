#!/usr/bin/env bash
# Sends a minute of lunch-rush traffic at the running lesson (lesson 006).
# Usage: scripts/lunch-rush.sh [-rate 5] [-duration 60s]   (flags go to the load generator)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -d /usr/local/go/bin ] && PATH="$PATH:/usr/local/go/bin"

LOADGEN="$ROOT/services/bin/loadgen"
if [ ! -x "$LOADGEN" ] || [ -n "$(find "$ROOT/services/loadgen" -name '*.go' -newer "$LOADGEN" -print -quit)" ]; then
  echo "Building the load generator..."
  (cd "$ROOT/services" && go build -o bin/ ./loadgen)
fi

exec "$LOADGEN" -base "http://localhost:${ORDER_PORT:-8080}" "$@"
