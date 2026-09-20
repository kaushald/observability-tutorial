// Command kitchen-service accepts tickets for restaurant kitchens and
// simulates prep time before returning.
package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"time"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"

	"github.com/kaushald/observability-tutorial/services/internal/httpx"
	"github.com/kaushald/observability-tutorial/services/internal/tracing"
)

// restaurantPrepMillis holds fixed prep times, in milliseconds, for
// restaurants that don't follow the standard per-item formula.
//
// Restaurant 2, "Slow Noodles", is the built-in timeout fault: its ticket
// always takes 2500ms, comfortably past the order-service's 2s deadline.
// Every other restaurant uses the standard formula in prepMillis.
var restaurantPrepMillis = map[int]int{
	2: 2500, // Slow Noodles
}

// prepMillis returns how long a kitchen ticket for restaurantID with
// itemCount items should take to prepare, in milliseconds.
func prepMillis(restaurantID, itemCount int) int {
	if ms, ok := restaurantPrepMillis[restaurantID]; ok {
		return ms
	}
	return 120 + 30*itemCount
}

type ticketRequest struct {
	OrderID      int `json:"orderId"`
	RestaurantID int `json:"restaurantId"`
	ItemCount    int `json:"itemCount"`
}

type ticketResponse struct {
	TicketID   string `json:"ticketId"`
	PrepMillis int    `json:"prepMillis"`
}

func handleTickets(w http.ResponseWriter, r *http.Request) {
	var req ticketRequest
	if err := httpx.ReadJSON(r, &req); err != nil {
		httpx.WriteError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	if req.OrderID == 0 || req.RestaurantID == 0 {
		httpx.WriteError(w, http.StatusBadRequest, "orderId and restaurantId are required")
		return
	}

	ticketID := fmt.Sprintf("T-%d", req.OrderID)
	log.Printf("ticket %s accepted for restaurant %d", ticketID, req.RestaurantID)

	ms := prepMillis(req.RestaurantID, req.ItemCount)
	time.Sleep(time.Duration(ms) * time.Millisecond)

	log.Printf("ticket %s ready", ticketID)

	httpx.WriteJSON(w, http.StatusOK, ticketResponse{TicketID: ticketID, PrepMillis: ms})
}

func handleHealthz(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte("ok"))
}

// newMux builds the kitchen-service handler: the app routes wrapped with
// otelhttp so incoming traceparent headers continue the caller's trace.
// Span names are the route (e.g. "POST /tickets"); health checks are
// filtered out entirely so they never produce spans.
func newMux() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /tickets", handleTickets)
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

	addr := ":8081"
	if p := os.Getenv("PORT"); p != "" {
		addr = ":" + p
	}

	log.Printf("kitchen-service listening on %s", addr)
	if err := httpx.Serve(addr, newMux(), shutdownTracing); err != nil {
		log.Fatal(err)
	}
}
