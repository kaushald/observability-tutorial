#!/usr/bin/env bash
# Builds every lesson jar and the Go services. The devcontainer runs this once
# (and a Codespaces prebuild runs it ahead of time), so run.sh only has to start things.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Building Go services..."
(cd "$ROOT/services" && go build -o bin/ ./kitchen-service ./delivery-service)

echo "Building order service jars..."
(cd "$ROOT" && ./gradlew --quiet bootJar)

echo "Build finished in ${SECONDS}s"
