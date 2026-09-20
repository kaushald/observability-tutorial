// Package httpx has small HTTP helpers shared by the tutorial services:
// JSON encode/decode, a plain request-logging wrapper, and a Serve func
// that runs an http.Server and shuts it down gracefully on SIGINT/SIGTERM.
package httpx

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

// ReadJSON decodes the request body into v. The caller is responsible for
// returning a 400 response when it returns an error.
func ReadJSON(r *http.Request, v any) error {
	defer r.Body.Close()
	return json.NewDecoder(r.Body).Decode(v)
}

// WriteJSON writes v as a JSON response with the given status code.
func WriteJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

// WriteError writes a JSON error body ({"error": msg}) with the given
// status code.
func WriteError(w http.ResponseWriter, status int, msg string) {
	WriteJSON(w, status, map[string]string{"error": msg})
}

// LogRequests wraps handler and logs one plain line per request with the
// method and path. It intentionally logs nothing about timing, so it never
// leaks duration information into logs that a lesson wants to keep
// deliberately unhelpful.
func LogRequests(handler http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		log.Printf("%s %s", r.Method, r.URL.Path)
		handler.ServeHTTP(w, r)
	})
}

// Serve runs handler on addr and blocks until the process receives
// SIGINT or SIGTERM. On signal, it shuts the HTTP server down gracefully
// and calls shutdownTracing, both bounded by a 5s deadline so spans get a
// chance to flush before the process exits. shutdownTracing may be nil.
func Serve(addr string, handler http.Handler, shutdownTracing func(context.Context) error) error {
	srv := &http.Server{
		Addr:    addr,
		Handler: handler,
	}

	serveErr := make(chan error, 1)
	go func() {
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			serveErr <- err
			return
		}
		serveErr <- nil
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	defer signal.Stop(stop)

	select {
	case err := <-serveErr:
		return err
	case <-stop:
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		log.Printf("http server shutdown: %v", err)
	}

	if shutdownTracing != nil {
		if err := shutdownTracing(ctx); err != nil {
			log.Printf("tracing shutdown: %v", err)
		}
	}

	<-serveErr
	return nil
}
