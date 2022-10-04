export OTEL_METRICS_EXPORTER="none"
export OTEL_EXPORTER_OTLP_ENDPOINT="https://api.honeycomb.io:443"
export OTEL_EXPORTER_OTLP_HEADERS="x-honeycomb-team=${HONEYCOMB_API_KEY}"
export OTEL_SERVICE_NAME="java-jokes"

cd go-joke-length || exit
go build -o bin/go-name main.go
bin/go-name
