// Package tracing wires up OpenTelemetry tracing for the tutorial services.
//
// Tracing is opt-in via the TRACING environment variable. Lesson 000 runs
// the exact same binaries with tracing off, so students see three
// disconnected log streams and no context propagation across services.
package tracing

import (
	"context"
	"os"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/exporters/stdout/stdouttrace"
	"go.opentelemetry.io/otel/propagation"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
)

// noopShutdown does nothing. It is returned when tracing is disabled so
// callers can always defer/call the returned shutdown func uniformly.
func noopShutdown(context.Context) error { return nil }

// Init configures tracing for the process.
//
// If the TRACING environment variable is not exactly "on", Init does
// nothing: no tracer provider is installed and no propagator is set. The
// global OpenTelemetry defaults (no-op tracer, no-op propagator) stay in
// place, so instrumented code is safe to call but produces no spans and
// does not propagate trace context.
//
// If TRACING=on, Init builds a span exporter: OTEL_TRACES_EXPORTER=console
// prints spans to stdout, anything else sends OTLP over HTTP, configured by
// the standard OTEL_EXPORTER_OTLP_ENDPOINT and OTEL_EXPORTER_OTLP_HEADERS
// variables. Only these two exporters are linked in, which keeps the build
// fast on a small Codespace. Init then installs a TracerProvider with a batch span
// processor and the default resource (service name from OTEL_SERVICE_NAME),
// sets it as the global tracer provider, and sets the global propagator to
// W3C TraceContext + Baggage. The returned shutdown func flushes and closes
// the exporter.
func Init(ctx context.Context) (shutdown func(context.Context) error, err error) {
	if os.Getenv("TRACING") != "on" {
		return noopShutdown, nil
	}

	exporter, err := newExporter(ctx)
	if err != nil {
		return noopShutdown, err
	}

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exporter),
	)

	otel.SetTracerProvider(tp)
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	))

	return tp.Shutdown, nil
}

func newExporter(ctx context.Context) (sdktrace.SpanExporter, error) {
	if os.Getenv("OTEL_TRACES_EXPORTER") == "console" {
		return stdouttrace.New()
	}
	return otlptracehttp.New(ctx)
}
