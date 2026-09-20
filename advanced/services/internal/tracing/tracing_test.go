package tracing

import (
	"context"
	"testing"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
	noop "go.opentelemetry.io/otel/trace/noop"
)

// header carries a single HTTP header for propagation.Extract via
// propagation.HeaderCarrier.
func header(key, value string) propagation.HeaderCarrier {
	h := propagation.HeaderCarrier{}
	h.Set(key, value)
	return h
}

func TestInit_DisabledByDefaultIsNoop(t *testing.T) {
	// TRACING is not set.
	shutdown, err := Init(context.Background())
	if err != nil {
		t.Fatalf("Init() error = %v, want nil", err)
	}
	if shutdown == nil {
		t.Fatal("Init() returned a nil shutdown func")
	}
	if err := shutdown(context.Background()); err != nil {
		t.Fatalf("no-op shutdown() error = %v, want nil", err)
	}

	// With tracing disabled, the global propagator must not extract or
	// inject any fields: it should behave like the default no-op
	// propagator, not TraceContext+Baggage.
	fields := otel.GetTextMapPropagator().Fields()
	if len(fields) != 0 {
		t.Errorf("propagator Fields() = %v, want none when tracing is disabled", fields)
	}

	// A traceparent header must not be picked up: the resulting span must
	// not be a recording span carrying the header's trace ID.
	ctx := otel.GetTextMapPropagator().Extract(context.Background(),
		header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
	_, span := otel.Tracer("tracing-test").Start(ctx, "should-not-record")
	defer span.End()
	if span.IsRecording() {
		t.Error("span.IsRecording() = true, want false when tracing is disabled")
	}
}

func TestInit_DisabledExplicitly(t *testing.T) {
	t.Setenv("TRACING", "off")
	shutdown, err := Init(context.Background())
	if err != nil {
		t.Fatalf("Init() error = %v, want nil", err)
	}
	if err := shutdown(context.Background()); err != nil {
		t.Fatalf("shutdown() error = %v, want nil", err)
	}
}

func TestInit_EnabledSetsPropagatorAndWorkingProvider(t *testing.T) {
	t.Setenv("TRACING", "on")
	t.Setenv("OTEL_TRACES_EXPORTER", "console")
	t.Setenv("OTEL_SERVICE_NAME", "tracing-test")

	// Reset global state after this test so it doesn't leak into other
	// tests in this package or others in the same binary.
	t.Cleanup(func() {
		otel.SetTracerProvider(noop.NewTracerProvider())
		otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator())
	})

	shutdown, err := Init(context.Background())
	if err != nil {
		t.Fatalf("Init() error = %v, want nil", err)
	}

	// The global propagator must now understand traceparent/tracestate/baggage.
	fields := otel.GetTextMapPropagator().Fields()
	if len(fields) == 0 {
		t.Error("propagator Fields() is empty, want traceparent/tracestate/baggage when tracing is enabled")
	}

	// A request carrying a traceparent header must produce a real,
	// recording span whose trace ID equals the one in the header.
	const wantTraceID = "4bf92f3577b34da6a3ce929d0e0e4736"
	ctx := otel.GetTextMapPropagator().Extract(context.Background(),
		header("traceparent", "00-"+wantTraceID+"-00f067aa0ba902b7-01"))
	_, span := otel.Tracer("tracing-test").Start(ctx, "server-span")
	defer span.End()

	if !span.IsRecording() {
		t.Fatal("span.IsRecording() = false, want true when tracing is enabled")
	}
	if got := span.SpanContext().TraceID().String(); got != wantTraceID {
		t.Errorf("span trace ID = %s, want %s", got, wantTraceID)
	}

	if err := shutdown(context.Background()); err != nil {
		t.Errorf("shutdown() error = %v, want nil", err)
	}
}
