#!/usr/bin/env bash
# Builds every lesson jar and the Go services. The devcontainer runs this once
# (and a Codespaces prebuild runs it ahead of time), so run.sh only has to start things.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -d /usr/local/go/bin ] && PATH="$PATH:/usr/local/go/bin"

# The Go build runs in the background while Gradle downloads and compiles
GO_LOG="$(mktemp)"
echo "Building Go services and order service jars..."
(cd "$ROOT/services" && go build -o bin/ ./kitchen-service ./delivery-service) > "$GO_LOG" 2>&1 &
GO_BUILD=$!

(cd "$ROOT" && ./gradlew --quiet bootJar)
echo "Order service jars built after ${SECONDS}s"

if ! wait "$GO_BUILD"; then
  echo "Go build failed:" >&2
  cat "$GO_LOG" >&2
  exit 1
fi
rm -f "$GO_LOG"

echo "Build finished in ${SECONDS}s"
