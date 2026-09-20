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

func TestETAMinutes(t *testing.T) {
	cases := []struct {
		orderID int
		want    int
	}{
		{0, 15},
		{17, 32},
		{20, 15},
		{25, 20},
		{39, 34},
	}
	for _, c := range cases {
		if got := etaMinutes(c.orderID); got != c.want {
			t.Errorf("etaMinutes(%d) = %d, want %d", c.orderID, got, c.want)
		}
	}
}

func TestNextDriver_Rotates(t *testing.T) {
	poolMu.Lock()
	poolNext = 0
	poolMu.Unlock()

	for i, want := range driverPool {
		if got := nextDriver(); got != want {
			t.Errorf("nextDriver() #%d = %q, want %q", i, got, want)
		}
	}
	// Rotation must wrap back to the start of the pool.
	if got := nextDriver(); got != driverPool[0] {
		t.Errorf("nextDriver() after wrap = %q, want %q", got, driverPool[0])
	}
}

func TestHandleAssignments_OK(t *testing.T) {
	body := bytes.NewBufferString(`{"orderId":17,"restaurantId":1}`)
	req := httptest.NewRequest(http.MethodPost, "/assignments", body)
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

	var resp assignmentResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decode response: %v", err)
	}

	found := false
	for _, d := range driverPool {
		if d == resp.Driver {
			found = true
			break
		}
	}
	if !found {
		t.Errorf("driver = %q, not in pool %v", resp.Driver, driverPool)
	}
	if resp.ETAMinutes != etaMinutes(17) {
		t.Errorf("etaMinutes = %d, want %d", resp.ETAMinutes, etaMinutes(17))
	}
}

func TestHandleAssignments_BadJSON(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/assignments", bytes.NewBufferString(`not json`))
	rec := httptest.NewRecorder()
	newMux().ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusBadRequest)
	}
}

func TestHandleAssignments_MissingFields(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/assignments", bytes.NewBufferString(`{}`))
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
	body := bytes.NewBufferString(`{"orderId":17,"restaurantId":1}`)
	req := httptest.NewRequest(http.MethodPost, "/assignments", body)
	req.Header.Set("traceparent", "00-"+wantTraceID+"-00f067aa0ba902b7-01")
	rec := httptest.NewRecorder()
	newMux().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, body = %s", rec.Code, rec.Body.String())
	}

	healthReq := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	healthRec := httptest.NewRecorder()
	newMux().ServeHTTP(healthRec, healthReq)

	if err := tp.Shutdown(context.Background()); err != nil {
		t.Fatalf("tp.Shutdown: %v", err)
	}

	spans := recorder.Ended()
	if len(spans) != 1 {
		t.Fatalf("got %d ended spans, want exactly 1 (assignment span only, no health check span): %+v", len(spans), spans)
	}
	span := spans[0]
	if got := span.Name(); got != "POST /assignments" {
		t.Errorf("span name = %q, want %q", got, "POST /assignments")
	}
	if got := span.SpanContext().TraceID().String(); got != wantTraceID {
		t.Errorf("span trace ID = %s, want %s", got, wantTraceID)
	}
}
