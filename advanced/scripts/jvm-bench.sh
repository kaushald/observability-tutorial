#!/usr/bin/env bash
# Compares JVMs for the order service: startup time and RSS, agent attached, 2 CPUs.
# Needs Docker and a built lesson 001 jar (scripts/build.sh). Usage: scripts/jvm-bench.sh
set -uo pipefail
ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
JAR="$ROOT/001-auto/order-service/build/libs/order-service.jar"
AGENT="$ROOT/../lib/opentelemetry-javaagent.jar"
PORT=18080
NAME=jvm-bench-run

run_case() {
  local label="$1" image="$2" opts="$3" agent="$4"
  local agent_opt=""
  [ "$agent" = "agent" ] && agent_opt="-javaagent:/app/agent.jar"
  docker rm -f "$NAME" >/dev/null 2>&1
  local t0 t1
  t0=$(python3 -c 'import time; print(time.time())')
  docker run -d --name "$NAME" --cpus=2 -m 2g -p "$PORT:8080" \
    -v "$JAR:/app/app.jar:ro" -v "$AGENT:/app/agent.jar:ro" -v jvm-bench-scc:/scc \
    -e OTEL_TRACES_EXPORTER=none -e OTEL_METRICS_EXPORTER=none -e OTEL_LOGS_EXPORTER=none \
    -e OTEL_SERVICE_NAME=order-service \
    "$image" sh -c "exec java $opts $agent_opt -jar /app/app.jar" >/dev/null
  local ok=""
  for _ in $(seq 1 600); do
    if curl -sf -o /dev/null "http://localhost:$PORT/api/restaurants"; then ok=1; break; fi
    sleep 0.1
  done
  t1=$(python3 -c 'import time; print(time.time())')
  if [ -z "$ok" ]; then
    echo "$label | FAILED TO START"; docker logs "$NAME" 2>&1 | tail -5; docker rm -f "$NAME" >/dev/null; return
  fi
  # a little traffic, then let it settle
  for i in $(seq 1 40); do
    curl -s -o /dev/null "http://localhost:$PORT/api/restaurants"
    curl -s -o /dev/null "http://localhost:$PORT/api/restaurants/1/menu"
  done
  for i in 1 2 3; do
    curl -s -o /dev/null -X POST "http://localhost:$PORT/api/orders" -H 'Content-Type: application/json' -d '{"restaurantId":1,"itemIds":[1]}'
  done
  sleep 3
  local rss spring
  rss=$(docker exec "$NAME" sh -c "grep VmRSS /proc/1/status" | awk '{ printf "%d", $2/1024 }')
  spring=$(docker logs "$NAME" 2>&1 | grep -o 'Started [A-Za-z]* in [0-9.]* seconds' | grep -o '[0-9.]* seconds' | head -1)
  printf '%-34s | ready %5.1fs | spring %-14s | RSS %4s MB\n' "$label" "$(python3 -c "print($t1-$t0)")" "$spring" "$rss"
  docker rm -f "$NAME" >/dev/null 2>&1
}

HOTSPOT=eclipse-temurin:17-jre
OPENJ9=ibm-semeru-runtimes:open-17-jre
docker pull -q "$HOTSPOT" >/dev/null
docker pull -q "$OPENJ9" >/dev/null
docker volume rm jvm-bench-scc >/dev/null 2>&1

echo "machine: $(uname -m), container limit 2 CPUs / 2 GB, every case run twice (second run has warm caches)"
for round in 1 2; do
  echo "--- round $round"
  run_case "hotspot default, no agent"        "$HOTSPOT" "-Xmx256m" noagent
  run_case "hotspot default"                  "$HOTSPOT" "-Xmx256m" agent
  run_case "hotspot serialgc"                 "$HOTSPOT" "-Xmx256m -XX:+UseSerialGC" agent
  run_case "hotspot serialgc+c1+xss"          "$HOTSPOT" "-Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k" agent
  run_case "openj9 default"                   "$OPENJ9"  "-Xmx256m" agent
  run_case "openj9 shareclasses+quickstart"   "$OPENJ9"  "-Xmx256m -Xshareclasses:name=orders,cacheDir=/scc -Xquickstart -Xtune:virtualized" agent
done
docker volume rm jvm-bench-scc >/dev/null 2>&1
