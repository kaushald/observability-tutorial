# 001: Tracing with no code changes

The app is a small food delivery system:

```
Browser -> order-service (Java, :8080) -> kitchen-service (Go, :8081)
                                       -> delivery-service (Go, :8082)
```

This lesson runs exactly the same code as `000-baseline`. The only differences are in
`run.sh`: the order service starts with the OpenTelemetry Java agent, and the Go services
start with `TRACING=on`.

## Run it

Put your Honeycomb API key in `.env` at the repository root (or set a Codespaces secret
named `HONEYCOMB_API_KEY`), then:

```
cd 001-auto
./run.sh
```

Open the URL it prints, order from Bella Pizza, then order from Slow Noodles.
Press Ctrl+C to stop. If something is left running, use `scripts/stop.sh`.

## What to look for in Honeycomb

1. One trace per order, crossing all three services, including the SQL queries the order
   service ran.
2. The Slow Noodles order: the `POST /tickets` span in `kitchen-service` runs for 2.5
   seconds, while the order service gives up after 2 seconds. In `000-baseline` the logs
   never told you that.

To print spans in the terminal instead of sending them to Honeycomb:

```
OTEL_TRACES_EXPORTER=console ./run.sh
```
