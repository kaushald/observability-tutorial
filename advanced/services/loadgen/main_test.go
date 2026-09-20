package main

import (
	"context"
	"encoding/json"
	"math"
	"math/rand"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

// testCatalog mirrors the three seeded restaurants and their menu item ids.
func testCatalog() catalog {
	return catalog{
		restaurants: []restaurant{
			{ID: 1, Name: "Bella Pizza"},
			{ID: 2, Name: "Slow Noodles"},
			{ID: 3, Name: "Taco Corner"},
		},
		items: map[int64][]int64{
			1: {1, 2, 3, 4, 5, 6, 7, 8},
			2: {9, 10, 11, 12, 13, 14, 15, 16},
			3: {17, 18, 19, 20, 21, 22, 23, 24},
		},
	}
}

func containsInt64(xs []int64, x int64) bool {
	for _, v := range xs {
		if v == x {
			return true
		}
	}
	return false
}

func TestPickRequest_Mix(t *testing.T) {
	cat := testCatalog()
	rng := rand.New(rand.NewSource(1))

	const draws = 10000
	counts := map[string]int{}
	orders := 0
	declined := 0

	for i := 0; i < draws; i++ {
		pr := pickRequest(rng, cat)
		counts[pr.shape]++

		switch pr.shape {
		case shapeMenu, shapeRestaurants:
			// no further checks
		case shapeOrders:
			orders++
			if len(pr.itemIDs) < 1 || len(pr.itemIDs) > 3 {
				t.Fatalf("order item count = %d, want 1-3", len(pr.itemIDs))
			}
			validItems := cat.items[pr.restaurantID]
			for _, id := range pr.itemIDs {
				if !containsInt64(validItems, id) {
					t.Fatalf("order item %d does not belong to restaurant %d", id, pr.restaurantID)
				}
			}
			if pr.cardNumber == cardDeclined {
				declined++
			} else if pr.cardNumber != cardOK {
				t.Fatalf("unexpected card number %q", pr.cardNumber)
			}
		default:
			t.Fatalf("unexpected shape %q", pr.shape)
		}
	}

	menuPct := float64(counts[shapeMenu]) / draws * 100
	restaurantsPct := float64(counts[shapeRestaurants]) / draws * 100
	ordersPct := float64(counts[shapeOrders]) / draws * 100

	if math.Abs(menuPct-50) > 3 {
		t.Errorf("menu pct = %.2f, want within 3pp of 50", menuPct)
	}
	if math.Abs(restaurantsPct-10) > 3 {
		t.Errorf("restaurants pct = %.2f, want within 3pp of 10", restaurantsPct)
	}
	if math.Abs(ordersPct-40) > 3 {
		t.Errorf("orders pct = %.2f, want within 3pp of 40", ordersPct)
	}

	declinedPct := float64(declined) / float64(orders) * 100
	if math.Abs(declinedPct-10) > 3 {
		t.Errorf("declined pct of orders = %.2f, want within 3pp of 10", declinedPct)
	}
}

func TestPercentile(t *testing.T) {
	tests := []struct {
		name   string
		values []float64
		p      float64
		want   float64
	}{
		{"empty", nil, 50, 0},
		{"single", []float64{42}, 50, 42},
		{"single p95", []float64{42}, 95, 42},
		{"p50 odd", []float64{1, 2, 3, 4, 5}, 50, 3},
		{"p100 is max", []float64{1, 2, 3, 4, 5}, 100, 5},
		{"p0 is min", []float64{1, 2, 3, 4, 5}, 0, 1},
		{"p95 interpolated", []float64{10, 20, 30, 40, 50}, 95, 48},
		{"unsorted input", []float64{5, 1, 3, 2, 4}, 50, 3},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := percentile(tt.values, tt.p)
			if math.Abs(got-tt.want) > 0.001 {
				t.Errorf("percentile(%v, %v) = %v, want %v", tt.values, tt.p, got, tt.want)
			}
		})
	}
}

func TestSummaryRender(t *testing.T) {
	sum := newSummary()
	sum.record(result{shape: shapeRestaurants, statusCode: 200, latencyMs: 12})
	sum.record(result{shape: shapeMenu, statusCode: 200, latencyMs: 20})
	sum.record(result{shape: shapeMenu, statusCode: 404, latencyMs: 8})
	sum.record(result{shape: shapeOrders, statusCode: 201, latencyMs: 100})
	sum.record(result{shape: shapeOrders, statusCode: 402, latencyMs: 50})
	sum.addDropped(3)

	out := sum.render()

	for _, shape := range []string{shapeRestaurants, shapeMenu, shapeOrders} {
		if !strings.Contains(out, shape) {
			t.Errorf("render() missing shape %q, got:\n%s", shape, out)
		}
	}
	for _, code := range []string{"200:1", "200:1", "404:1", "201:1", "402:1"} {
		if !strings.Contains(out, code) {
			t.Errorf("render() missing status code count %q, got:\n%s", code, out)
		}
	}
	if !strings.Contains(out, "3") {
		t.Errorf("render() missing dropped count, got:\n%s", out)
	}
}

// fakeOrderService fakes just enough of the order-service API for an
// end-to-end run: a restaurant list, a menu per restaurant, and an orders
// endpoint that declines the fixed test card number.
func fakeOrderService(t *testing.T) *httptest.Server {
	t.Helper()

	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/restaurants", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode([]map[string]any{
			{"id": 1, "name": "Bella Pizza", "cuisine": "Italian"},
			{"id": 2, "name": "Slow Noodles", "cuisine": "Asian"},
			{"id": 3, "name": "Taco Corner", "cuisine": "Mexican"},
		})
	})
	mux.HandleFunc("GET /api/restaurants/{id}/menu", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode([]map[string]any{
			{"id": 1, "name": "Item One", "priceCents": 500, "tags": []string{}},
			{"id": 2, "name": "Item Two", "priceCents": 600, "tags": []string{}},
			{"id": 3, "name": "Item Three", "priceCents": 700, "tags": []string{}},
		})
	})
	mux.HandleFunc("POST /api/orders", func(w http.ResponseWriter, r *http.Request) {
		var body struct {
			CardNumber string `json:"cardNumber"`
		}
		_ = json.NewDecoder(r.Body).Decode(&body)
		w.Header().Set("Content-Type", "application/json")
		if strings.Contains(body.CardNumber, "0000 0000 0000") {
			w.WriteHeader(http.StatusPaymentRequired)
			_ = json.NewEncoder(w).Encode(map[string]any{"orderId": 1, "status": "DECLINED"})
			return
		}
		w.WriteHeader(http.StatusCreated)
		_ = json.NewEncoder(w).Encode(map[string]any{"orderId": 1, "status": "CONFIRMED"})
	})

	return httptest.NewServer(mux)
}

func TestEndToEnd(t *testing.T) {
	server := fakeOrderService(t)
	defer server.Close()

	client := &http.Client{Timeout: 5 * time.Second}
	cat, err := fetchCatalog(client, server.URL)
	if err != nil {
		t.Fatalf("fetchCatalog() error = %v", err)
	}

	sum := newSummary()
	cfg := config{
		base:     server.URL,
		rate:     50,
		duration: time.Second,
		seed:     1,
		workers:  8,
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	run(ctx, cfg, cat, sum)

	total := sum.totalRequests()
	// At 50rps for ~1s we expect on the order of 50 requests, generously
	// bounded to absorb scheduling jitter in CI.
	if total < 10 || total > 200 {
		t.Errorf("total requests = %d, want roughly 10-200", total)
	}

	out := sum.render()
	for _, shape := range []string{shapeRestaurants, shapeMenu, shapeOrders} {
		if !strings.Contains(out, shape) {
			t.Errorf("render() missing shape %q after end-to-end run, got:\n%s", shape, out)
		}
	}
}
