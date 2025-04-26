# Load environment variables from .env file in the root folder
if [ -f ../.env ]; then
  export $(grep -v '^#' ../.env | xargs)
fi

# Ensure HONEYCOMB_API_KEY is set
if [ -z "$HONEYCOMB_API_KEY" ]; then
  echo "Error: HONEYCOMB_API_KEY is not set in the .env file." >&2
  exit 1
fi

export OTEL_METRICS_EXPORTER="none"
export OTEL_EXPORTER_OTLP_ENDPOINT="https://api.honeycomb.io:443"
export OTEL_EXPORTER_OTLP_HEADERS="x-honeycomb-team=${HONEYCOMB_API_KEY}"
export OTEL_SERVICE_NAME="go-jokes"

cd go-joke-length || exit
go build -o bin/go-name main.go
bin/go-name
