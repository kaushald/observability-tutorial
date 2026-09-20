// Command delivery-service assigns a driver and an ETA for an order, and sends
// customer notifications.
package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"sync"
	"time"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"

	"github.com/kaushald/observability-tutorial/services/internal/httpx"
	"github.com/kaushald/observability-tutorial/services/internal/tracing"
)

// driverPool is the in-memory pool assignments rotate through, round-robin.
var driverPool = []string{"Priya", "Marcus", "Wei", "Fatima", "Noah"}

var (
	poolMu   sync.Mutex
	poolNext int
)

// nextDriver returns the next driver from driverPool, round-robin, guarded
// by poolMu.
func nextDriver() string {
	poolMu.Lock()
	defer poolMu.Unlock()
	d := driverPool[poolNext%len(driverPool)]
	poolNext++
	return d
}

// etaMinutes computes a deterministic ETA for an order.
func etaMinutes(orderID int) int {
	return 15 + (orderID % 20)
}

type assignmentRequest struct {
	OrderID      int `json:"orderId"`
	RestaurantID int `json:"restaurantId"`
}

type assignmentResponse struct {
	Driver     string `json:"driver"`
	ETAMinutes int    `json:"etaMinutes"`
}

func handleAssignments(w http.ResponseWriter, r *http.Request) {
	var req assignmentRequest
	if err := httpx.ReadJSON(r, &req); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	if req.OrderID == 0 || req.RestaurantID == 0 {
		httpx.WriteError(w, http.StatusBadRequest, "orderId and restaurantId are required")
		return
	}

	// Simulate the work of finding and notifying a driver.
	time.Sleep(40 * time.Millisecond)

	resp := assignmentResponse{
		Driver:     nextDriver(),
		ETAMinutes: etaMinutes(req.OrderID),
	}
	httpx.WriteJSON(w, http.StatusOK, resp)
}

type notificationRequest struct {
	OrderID int    `json:"orderId"`
	Message string `json:"message"`
}

func handleNotifications(w http.ResponseWriter, r *http.Request) {
	var req notificationRequest
	if err := httpx.ReadJSON(r, &req); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	if req.OrderID == 0 {
		httpx.WriteError(w, http.StatusBadRequest, "orderId is required")
		return
	}

	// Simulate handing the message to an SMS provider.
	time.Sleep(60 * time.Millisecond)
	log.Printf("notification sent for order %d", req.OrderID)
	w.WriteHeader(http.StatusAccepted)
}

func handleHealthz(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte("ok"))
}

// newMux builds the delivery-service handler: the app routes wrapped with
// otelhttp so incoming traceparent headers continue the caller's trace.
// Span names are the route (e.g. "POST /assignments"); health checks are
// filtered out entirely so they never produce spans.
func newMux() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /assignments", handleAssignments)
	mux.HandleFunc("POST /notifications", handleNotifications)
	mux.HandleFunc("GET /healthz", handleHealthz)

	return otelhttp.NewHandler(mux, "",
		otelhttp.WithFilter(func(r *http.Request) bool {
			return r.URL.Path != "/healthz"
		}),
		otelhttp.WithSpanNameFormatter(func(_ string, r *http.Request) string {
			return r.Method + " " + r.URL.Path
		}),
	)
}

func main() {
	ctx := context.Background()
	shutdownTracing, err := tracing.Init(ctx)
	if err != nil {
		log.Fatalf("tracing init: %v", err)
	}

	addr := ":8082"
	if p := os.Getenv("PORT"); p != "" {
		addr = ":" + p
	}

	log.Printf("delivery-service listening on %s", addr)
	if err := httpx.Serve(addr, newMux(), shutdownTracing); err != nil {
		log.Fatal(err)
	}
}
