# Sourced by the lesson scripts. The one place for OpenTelemetry and JVM settings.
# Expects ROOT to be set to the advanced/ directory. The API key file and the Java agent
# live one level up, shared with the basic lessons.
REPO_ROOT="$(dirname "$ROOT")"

# API key: a Codespaces secret named HONEYCOMB_API_KEY wins, otherwise read .env
if [ -z "${HONEYCOMB_API_KEY:-}" ] && [ -f "$REPO_ROOT/.env" ]; then
  set -a
  . "$REPO_ROOT/.env"
  set +a
fi

# otlp sends to Honeycomb. console prints spans to the terminal (used by smoke.sh).
export OTEL_TRACES_EXPORTER="${OTEL_TRACES_EXPORTER:-otlp}"
export OTEL_METRICS_EXPORTER="none"
export OTEL_LOGS_EXPORTER="none"
# Send spans every second instead of every five, so traces show up quickly in class
export OTEL_BSP_SCHEDULE_DELAY="${OTEL_BSP_SCHEDULE_DELAY:-1000}"

if [ "$OTEL_TRACES_EXPORTER" = "otlp" ] && [ "${TRACING:-on}" = "on" ]; then
  if [ -z "${HONEYCOMB_API_KEY:-}" ]; then
    echo "Error: HONEYCOMB_API_KEY is not set. Add it to .env or as a Codespaces secret." >&2
    exit 1
  fi
  # To use another backend, change these two lines
  export OTEL_EXPORTER_OTLP_ENDPOINT="https://api.honeycomb.io:443"
  export OTEL_EXPORTER_OTLP_HEADERS="x-honeycomb-team=${HONEYCOMB_API_KEY}"
  export OTEL_EXPORTER_OTLP_PROTOCOL="http/protobuf"
fi

# Ports. Override these if something else on your machine already uses them.
export ORDER_PORT="${ORDER_PORT:-8080}"
export KITCHEN_PORT="${KITCHEN_PORT:-8081}"
export DELIVERY_PORT="${DELIVERY_PORT:-8082}"

# Small heap, serial collector, C1 compiler only and smaller thread stacks: the fastest start
# and lowest memory of the options measured in docs/jvm-comparison.md
JAVA_OPTS="${JAVA_OPTS:--Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k}"
JAVA_AGENT="$REPO_ROOT/lib/opentelemetry-javaagent.jar"
