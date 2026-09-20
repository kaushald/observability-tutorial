#!/usr/bin/env bash
# Stops anything a lesson left running: first the recorded pids, then whatever
# still listens on the lesson ports.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ -f "$ROOT/.run/pids" ]; then
  xargs kill < "$ROOT/.run/pids" 2>/dev/null
  rm -f "$ROOT/.run/pids"
fi

if command -v lsof >/dev/null; then
  for port in "${ORDER_PORT:-8080}" "${KITCHEN_PORT:-8081}" "${DELIVERY_PORT:-8082}"; do
    pids="$(lsof -ti "tcp:$port" -sTCP:LISTEN 2>/dev/null)"
    if [ -n "$pids" ]; then
      echo "Stopping process on port $port"
      echo "$pids" | xargs kill 2>/dev/null
    fi
  done
fi
echo "Stopped."
