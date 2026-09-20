package httpx

import (
	"bytes"
	"context"
	"encoding/json"
	"log"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"syscall"
	"testing"
	"time"
)

type payload struct {
	Name string `json:"name"`
}

func TestReadJSON_OK(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(`{"name":"noodles"}`))
	var p payload
	if err := ReadJSON(req, &p); err != nil {
		t.Fatalf("ReadJSON() error = %v, want nil", err)
	}
	if p.Name != "noodles" {
		t.Errorf("Name = %q, want %q", p.Name, "noodles")
	}
}

func TestReadJSON_BadJSON(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(`not json`))
	var p payload
	if err := ReadJSON(req, &p); err == nil {
		t.Fatal("ReadJSON() error = nil, want error for malformed JSON")
	}
}

func TestWriteJSON(t *testing.T) {
	rec := httptest.NewRecorder()
	WriteJSON(rec, http.StatusCreated, payload{Name: "noodles"})

	if rec.Code != http.StatusCreated {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusCreated)
	}
	if ct := rec.Header().Get("Content-Type"); !strings.HasPrefix(ct, "application/json") {
		t.Errorf("Content-Type = %q, want application/json", ct)
	}

	var p payload
	if err := json.Unmarshal(rec.Body.Bytes(), &p); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if p.Name != "noodles" {
		t.Errorf("Name = %q, want %q", p.Name, "noodles")
	}
}

func TestWriteError(t *testing.T) {
	rec := httptest.NewRecorder()
	WriteError(rec, http.StatusBadRequest, "bad request")

	if rec.Code != http.StatusBadRequest {
		t.Errorf("status = %d, want %d", rec.Code, http.StatusBadRequest)
	}
	var body map[string]string
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body["error"] != "bad request" {
		t.Errorf("error = %q, want %q", body["error"], "bad request")
	}
}

func TestLogRequests_CallsHandlerAndLogs(t *testing.T) {
	var buf bytes.Buffer
	log.SetOutput(&buf)
	t.Cleanup(func() { log.SetOutput(os.Stderr) })

	called := false
	inner := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		called = true
		w.WriteHeader(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	rec := httptest.NewRecorder()
	LogRequests(inner).ServeHTTP(rec, req)

	if !called {
		t.Error("LogRequests did not call the wrapped handler")
	}
	logged := buf.String()
	if !strings.Contains(logged, "GET") || !strings.Contains(logged, "/healthz") {
		t.Errorf("log output = %q, want it to mention method and path", logged)
	}
}

// TestServe_ShutsDownOnSIGTERM starts a server via Serve in a goroutine,
// sends SIGTERM to this process, and checks Serve returns and flushes
// tracing within the 5s budget.
func TestServe_ShutsDownOnSIGTERM(t *testing.T) {
	tracingShutdownCalled := make(chan struct{}, 1)
	shutdownTracing := func(ctx context.Context) error {
		select {
		case tracingShutdownCalled <- struct{}{}:
		default:
		}
		return nil
	}

	done := make(chan error, 1)
	go func() {
		done <- Serve("127.0.0.1:0", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusOK)
		}), shutdownTracing)
	}()

	// Give the server a moment to start, then signal this process.
	time.Sleep(100 * time.Millisecond)
	if err := syscall.Kill(os.Getpid(), syscall.SIGTERM); err != nil {
		t.Fatalf("sending SIGTERM to self: %v", err)
	}

	select {
	case err := <-done:
		if err != nil {
			t.Errorf("Serve() error = %v, want nil", err)
		}
	case <-time.After(6 * time.Second):
		t.Fatal("Serve() did not return within 6s of SIGTERM")
	}

	select {
	case <-tracingShutdownCalled:
	default:
		t.Error("Serve() did not call shutdownTracing")
	}
}
