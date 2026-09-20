// Command loadgen simulates a lunch rush against the order service: a
// steady stream of menu browsing and order placement, split across the
// seeded restaurants so the slow one takes its share of the traffic.
//
// It is deliberately dependency-free (standard library only) so it builds
// and runs anywhere the rest of the Go tutorial services do.
package main

import (
	"bytes"
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"math"
	"math/rand"
	"net/http"
	"os"
	"os/signal"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"
)

// Endpoint shapes: the buckets results are grouped into, independent of
// which restaurant or item ids a particular request used.
const (
	shapeRestaurants = "GET /api/restaurants"
	shapeMenu        = "GET /api/restaurants/{id}/menu"
	shapeOrders      = "POST /api/orders"
)

// Card numbers used to place orders. One in ten orders uses the declined
// card; the rest use the one that always succeeds.
const (
	cardOK       = "4242 4242 4242 4242"
	cardDeclined = "4000 0000 0000 0000"
)

type config struct {
	base     string
	rate     int
	duration time.Duration
	seed     int64
	workers  int
}

// restaurant is the subset of the restaurant list response loadgen needs.
type restaurant struct {
	ID   int64
	Name string
}

// catalog is what loadgen learns from the order service at startup: the
// restaurants and, for each one, the ids of the items on its menu.
type catalog struct {
	restaurants []restaurant
	items       map[int64][]int64
}

func (c catalog) empty() bool {
	return len(c.restaurants) == 0
}

// pickedRequest describes one request to send, without any knowledge of
// how to actually send it. Keeping it separate from the HTTP call is what
// makes pickRequest testable without a network.
type pickedRequest struct {
	shape        string
	restaurantID int64
	itemIDs      []int64
	cardNumber   string
}

// pickRequest chooses the next request to send: 50% a menu lookup, 10% the
// restaurant list, 40% an order. Restaurant choice is uniform, so each
// restaurant (including the slow one) gets roughly a third of the traffic
// that touches a specific restaurant.
func pickRequest(rng *rand.Rand, cat catalog) pickedRequest {
	if cat.empty() {
		return pickedRequest{shape: shapeRestaurants}
	}

	roll := rng.Float64() * 100
	switch {
	case roll < 50:
		r := cat.restaurants[rng.Intn(len(cat.restaurants))]
		return pickedRequest{shape: shapeMenu, restaurantID: r.ID}
	case roll < 60:
		return pickedRequest{shape: shapeRestaurants}
	default:
		r := cat.restaurants[rng.Intn(len(cat.restaurants))]
		menu := cat.items[r.ID]
		card := cardOK
		if rng.Intn(10) == 0 {
			card = cardDeclined
		}
		if len(menu) == 0 {
			return pickedRequest{shape: shapeOrders, restaurantID: r.ID, cardNumber: card}
		}
		n := 1 + rng.Intn(3)
		itemIDs := make([]int64, n)
		for i := 0; i < n; i++ {
			itemIDs[i] = menu[rng.Intn(len(menu))]
		}
		return pickedRequest{shape: shapeOrders, restaurantID: r.ID, itemIDs: itemIDs, cardNumber: card}
	}
}

// percentile returns the p-th percentile (0-100) of values using linear
// interpolation between closest ranks. It does not mutate values. An empty
// slice returns 0.
func percentile(values []float64, p float64) float64 {
	if len(values) == 0 {
		return 0
	}
	sorted := append([]float64(nil), values...)
	sort.Float64s(sorted)
	if len(sorted) == 1 {
		return sorted[0]
	}

	rank := p / 100 * float64(len(sorted)-1)
	lo := int(math.Floor(rank))
	hi := int(math.Ceil(rank))
	if lo == hi {
		return sorted[lo]
	}
	frac := rank - float64(lo)
	return sorted[lo] + (sorted[hi]-sorted[lo])*frac
}

// result records the outcome of one sent request.
type result struct {
	shape      string
	statusCode int // 0 when the request itself failed (timeout, connection refused, ...)
	latencyMs  float64
	err        error
}

type shapeStats struct {
	count        int
	errCount     int
	statusCounts map[int]int
	latenciesMs  []float64
}

// summary accumulates results and dropped ticks over a run and renders
// them as a plain, aligned table grouped by endpoint shape.
type summary struct {
	mu      sync.Mutex
	shapes  map[string]*shapeStats
	dropped int64
}

func newSummary() *summary {
	return &summary{shapes: make(map[string]*shapeStats)}
}

func (s *summary) record(r result) {
	s.mu.Lock()
	defer s.mu.Unlock()

	st, ok := s.shapes[r.shape]
	if !ok {
		st = &shapeStats{statusCounts: make(map[int]int)}
		s.shapes[r.shape] = st
	}
	st.count++
	st.latenciesMs = append(st.latenciesMs, r.latencyMs)
	if r.err != nil {
		st.errCount++
	} else {
		st.statusCounts[r.statusCode]++
	}
}

func (s *summary) addDropped(n int64) {
	atomic.AddInt64(&s.dropped, n)
}

func (s *summary) totalRequests() int {
	s.mu.Lock()
	defer s.mu.Unlock()

	total := 0
	for _, st := range s.shapes {
		total += st.count
	}
	return total
}

// shapeOrder fixes the row order in the rendered table so it reads the
// same way every time, regardless of map iteration order.
var shapeOrder = []string{shapeRestaurants, shapeMenu, shapeOrders}

func (s *summary) render() string {
	s.mu.Lock()
	defer s.mu.Unlock()

	var b strings.Builder
	fmt.Fprintf(&b, "%-30s %8s %8s %8s %8s  %s\n", "endpoint", "count", "p50ms", "p95ms", "maxms", "status codes")

	total := 0
	for _, shape := range shapeOrder {
		st, ok := s.shapes[shape]
		if !ok {
			continue
		}
		total += st.count

		codes := make([]int, 0, len(st.statusCounts))
		for code := range st.statusCounts {
			codes = append(codes, code)
		}
		sort.Ints(codes)

		parts := make([]string, 0, len(codes)+1)
		for _, code := range codes {
			parts = append(parts, fmt.Sprintf("%d:%d", code, st.statusCounts[code]))
		}
		if st.errCount > 0 {
			parts = append(parts, fmt.Sprintf("err:%d", st.errCount))
		}

		fmt.Fprintf(&b, "%-30s %8d %8.1f %8.1f %8.1f  %s\n",
			shape, st.count,
			percentile(st.latenciesMs, 50),
			percentile(st.latenciesMs, 95),
			percentile(st.latenciesMs, 100),
			strings.Join(parts, " "))
	}

	fmt.Fprintf(&b, "%-30s %8d\n", "total", total)
	fmt.Fprintf(&b, "%-30s %8d\n", "dropped", s.dropped)
	return b.String()
}

func drainAndClose(body io.ReadCloser) {
	_, _ = io.Copy(io.Discard, body)
	_ = body.Close()
}

// fetchCatalog learns the restaurants and their menus from the order
// service so pickRequest has real ids to work with. It returns a clear
// error when the order service can't be reached at all.
func fetchCatalog(client *http.Client, base string) (catalog, error) {
	cat := catalog{items: make(map[int64][]int64)}

	resp, err := client.Get(base + "/api/restaurants")
	if err != nil {
		return cat, fmt.Errorf("order service not reachable at %s: %w", base, err)
	}
	body, readErr := io.ReadAll(resp.Body)
	resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return cat, fmt.Errorf("GET /api/restaurants: unexpected status %d", resp.StatusCode)
	}
	if readErr != nil {
		return cat, fmt.Errorf("reading /api/restaurants response: %w", readErr)
	}

	var restaurants []struct {
		ID   int64  `json:"id"`
		Name string `json:"name"`
	}
	if err := json.Unmarshal(body, &restaurants); err != nil {
		return cat, fmt.Errorf("decoding /api/restaurants response: %w", err)
	}
	if len(restaurants) == 0 {
		return cat, fmt.Errorf("order service at %s returned no restaurants", base)
	}

	for _, r := range restaurants {
		menuResp, err := client.Get(fmt.Sprintf("%s/api/restaurants/%d/menu", base, r.ID))
		if err != nil {
			return cat, fmt.Errorf("order service not reachable fetching menu for restaurant %d: %w", r.ID, err)
		}
		menuBody, readErr := io.ReadAll(menuResp.Body)
		menuResp.Body.Close()
		if menuResp.StatusCode != http.StatusOK {
			return cat, fmt.Errorf("GET /api/restaurants/%d/menu: unexpected status %d", r.ID, menuResp.StatusCode)
		}
		if readErr != nil {
			return cat, fmt.Errorf("reading menu response for restaurant %d: %w", r.ID, readErr)
		}

		var items []struct {
			ID int64 `json:"id"`
		}
		if err := json.Unmarshal(menuBody, &items); err != nil {
			return cat, fmt.Errorf("decoding menu response for restaurant %d: %w", r.ID, err)
		}

		itemIDs := make([]int64, len(items))
		for i, item := range items {
			itemIDs[i] = item.ID
		}

		cat.restaurants = append(cat.restaurants, restaurant{ID: r.ID, Name: r.Name})
		cat.items[r.ID] = itemIDs
	}

	return cat, nil
}

func msSince(start time.Time) float64 {
	return float64(time.Since(start)) / float64(time.Millisecond)
}

// sendRequest issues the HTTP call described by pr and times it. Every
// response body is drained and closed so keep-alive connections are
// reused instead of leaking.
func sendRequest(client *http.Client, base string, pr pickedRequest) result {
	start := time.Now()

	var (
		resp *http.Response
		err  error
	)
	switch pr.shape {
	case shapeRestaurants:
		resp, err = client.Get(base + "/api/restaurants")
	case shapeMenu:
		resp, err = client.Get(fmt.Sprintf("%s/api/restaurants/%d/menu", base, pr.restaurantID))
	case shapeOrders:
		payload, _ := json.Marshal(struct {
			RestaurantID int64   `json:"restaurantId"`
			ItemIDs      []int64 `json:"itemIds"`
			CardNumber   string  `json:"cardNumber"`
		}{pr.restaurantID, pr.itemIDs, pr.cardNumber})
		resp, err = client.Post(base+"/api/orders", "application/json", bytes.NewReader(payload))
	}

	if err != nil {
		return result{shape: pr.shape, latencyMs: msSince(start), err: err}
	}
	defer drainAndClose(resp.Body)
	return result{shape: pr.shape, statusCode: resp.StatusCode, latencyMs: msSince(start)}
}

// run drives the load for cfg.duration (or until ctx is cancelled): a
// ticker at the configured rate feeds a bounded worker pool. When every
// worker is busy, the tick is dropped rather than queued without bound.
// run blocks until every in-flight request has finished.
func run(ctx context.Context, cfg config, cat catalog, sum *summary) {
	rng := rand.New(rand.NewSource(cfg.seed))
	client := &http.Client{Timeout: 5 * time.Second}

	interval := time.Second / time.Duration(cfg.rate)
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	progress := time.NewTicker(10 * time.Second)
	defer progress.Stop()

	deadline := time.NewTimer(cfg.duration)
	defer deadline.Stop()

	sem := make(chan struct{}, cfg.workers)
	var wg sync.WaitGroup
	var sent int64

loop:
	for {
		select {
		case <-ctx.Done():
			break loop
		case <-deadline.C:
			break loop
		case <-progress.C:
			fmt.Printf("... %d requests sent so far\n", atomic.LoadInt64(&sent))
		case <-ticker.C:
			select {
			case sem <- struct{}{}:
				pr := pickRequest(rng, cat)
				atomic.AddInt64(&sent, 1)
				wg.Add(1)
				go func() {
					defer wg.Done()
					defer func() { <-sem }()
					sum.record(sendRequest(client, cfg.base, pr))
				}()
			default:
				sum.addDropped(1)
			}
		}
	}

	wg.Wait()
}

func realMain() int {
	base := flag.String("base", "http://localhost:8080", "base URL of the order service")
	rate := flag.Int("rate", 5, "requests per second")
	duration := flag.Duration("duration", 60*time.Second, "how long to run")
	seed := flag.Int64("seed", 1, "seed for the request mix, for a reproducible run")
	workers := flag.Int("workers", 8, "maximum concurrent requests")
	flag.Parse()

	if *rate <= 0 {
		fmt.Fprintln(os.Stderr, "loadgen: -rate must be greater than 0")
		return 1
	}
	if *workers <= 0 {
		fmt.Fprintln(os.Stderr, "loadgen: -workers must be greater than 0")
		return 1
	}

	client := &http.Client{Timeout: 5 * time.Second}
	cat, err := fetchCatalog(client, *base)
	if err != nil {
		fmt.Fprintf(os.Stderr, "loadgen: %v\n", err)
		return 1
	}
	fmt.Printf("loadgen: learned %d restaurants from %s\n", len(cat.restaurants), *base)

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	cfg := config{base: *base, rate: *rate, duration: *duration, seed: *seed, workers: *workers}
	sum := newSummary()

	fmt.Printf("loadgen: sending ~%d req/s for %s (workers=%d, seed=%d)\n", cfg.rate, cfg.duration, cfg.workers, cfg.seed)
	run(ctx, cfg, cat, sum)

	fmt.Println()
	fmt.Print(sum.render())
	return 0
}

func main() {
	os.Exit(realMain())
}
