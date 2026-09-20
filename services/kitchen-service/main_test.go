package main

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"
)

func TestPrepMillis(t *testing.T) {
	cases := []struct {
		name                     string
		restaurantID, itemCount int
		want                     int
	}{
		{"slow noodles flat rate", 2, 1, 2500},
		{"slow noodles ignores item count", 2, 10, 2500},
		{"standard formula two items", 1, 2, 180},
		{"standard formula zero items", 5, 0, 120},
		{"standard formula more items", 7, 4, 240},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got := prepMillis(c.restaurantID, c.itemCount)
			if got != c.want {
				t.Errorf("prepMillis(%d, %d) = %d, want %d", c.restaurantID, c.itemCount, got, c.want)
			}
		})
	}
}

func TestHandleTickets_OK(t *testing.T) {
	body := bytes.NewBufferString(`{"orderId":17,"restaurantId":1,"itemCount":2}`)
	req := httptest.NewRequest(http.MethodPost, "/tickets", body)
	rec := httptest.NewRecorder()

	start := time.Now()
	newMux().ServeHTTP(rec, req)
	elapsed := time.Since(start)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, body = %s", rec.Code, rec.Body.String())
	}
	if elapsed > time.Second {
		t.Fatalf("request took %v, want well under 1s", elapsed)
	}

	var resp ticketResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if resp.TicketID != "T-17" {
		t.Errorf("ticketId = %q, want T-17", resp.TicketID)
	}
	if resp.PrepMillis != 180 {
		t.Errorf("prepMillis = %d, want 180", resp.PrepMillis)
	}
}

func TestHandleTickets_BadJSON(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/tickets", bytes.NewBufferString(`not json`))
	rec := httptest.NewRecorder()
	newMux().ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusBadRequest)
	}
}

func TestHandleTickets_MissingFields(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/tickets", bytes.NewBufferString(`{"itemCount":2}`))
	rec := httptest.NewRecorder()
	newMux().ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusBadRequest)
	}
}

func TestHealthz(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	rec := httptest.NewRecorder()
	newMux().ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusOK)
	}
	if rec.Body.String() != "ok" {
		t.Errorf("body = %q, want %q", rec.Body.String(), "ok")
	}
}

// TestTracing_ServerSpanNamesRouteAndContinuesTrace verifies that
// newMux()'s otelhttp wiring names spans after the route and continues an
// incoming W3C trace, and that health checks produce no span at all.
func TestTracing_ServerSpanNamesRouteAndContinuesTrace(t *testing.T) {
	recorder := tracetest.NewSpanRecorder()
	tp := sdktrace.NewTracerProvider(sdktrace.WithSpanProcessor(recorder))
	origProvider := otel.GetTracerProvider()
	origPropagator := otel.GetTextMapPropagator()
	otel.SetTracerProvider(tp)
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{}, propagation.Baggage{},
	))
	t.Cleanup(func() {
		otel.SetTracerProvider(origProvider)
		otel.SetTextMapPropagator(origPropagator)
	})

	const wantTraceID = "4bf92f3577b34da6a3ce929d0e0e4736"
	body := bytes.NewBufferString(`{"orderId":17,"restaurantId":1,"itemCount":2}`)
	req := httptest.NewRequest(http.MethodPost, "/tickets", body)
	req.Header.Set("traceparent", "00-"+wantTraceID+"-00f067aa0ba902b7-01")
	rec := httptest.NewRecorder()
	newMux().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, body = %s", rec.Code, rec.Body.String())
	}

	// Health checks must not produce spans.
	healthReq := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	healthRec := httptest.NewRecorder()
	newMux().ServeHTTP(healthRec, healthReq)

	if err := tp.Shutdown(context.Background()); err != nil {
		t.Fatalf("tp.Shutdown: %v", err)
	}

	spans := recorder.Ended()
	if len(spans) != 1 {
		t.Fatalf("got %d ended spans, want exactly 1 (ticket span only, no health check span): %+v", len(spans), spans)
	}

	span := spans[0]
	if got := span.Name(); got != "POST /tickets" {
		t.Errorf("span name = %q, want %q", got, "POST /tickets")
	}
	if got := span.SpanContext().TraceID().String(); got != wantTraceID {
		t.Errorf("span trace ID = %s, want %s", got, wantTraceID)
	}
}
