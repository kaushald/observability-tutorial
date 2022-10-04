package main

import (
	"context"
	"fmt"
	"io/ioutil"
	"log"
	"math/rand"
	"net/http"
	"os"
	"strconv"
	"time"

	beeline "github.com/honeycombio/beeline-go"
	"github.com/honeycombio/beeline-go/propagation"
	"github.com/honeycombio/beeline-go/wrappers/config"
	"github.com/honeycombio/beeline-go/wrappers/hnynethttp"
)

func main() {
	beeline.Init(beeline.Config{
		WriteKey:    os.Getenv("HONEYCOMB_API_KEY"),
		Dataset:     os.Getenv("HONEYCOMB_DATASET"),
		ServiceName: "go-joke-length",
	})
	defer beeline.Close()

	mux := http.NewServeMux()
	mux.HandleFunc("/length", func(w http.ResponseWriter, r *http.Request) {
		rand.Seed(time.Now().UnixNano())
		time.Sleep(time.Duration(rand.Intn(250)) * time.Millisecond)
		joke, _ := getJoke(r.Context())
		time.Sleep(time.Duration(rand.Intn(250)) * time.Millisecond)
		length := len([]rune(joke))
		//fmt.Println(joke + " [" + strconv.Itoa(length) + "]")
		fmt.Fprintf(w, joke+" ["+strconv.Itoa(length)+"]")
	})

	log.Println("Listening on ", ":8085")
	log.Fatal(http.ListenAndServe(":8085", hnynethttp.WrapHandler(mux)))
}

func getJoke(ctx context.Context) (string, context.Context) {
	ctx, span := beeline.StartSpan(ctx, "call /joke")
	defer span.Send()
	time.Sleep(time.Duration(rand.Intn(250)) * time.Millisecond)
	req, _ := http.NewRequestWithContext(ctx, "GET", "http://localhost:8086/joke", nil)
	client := &http.Client{
		Transport: hnynethttp.WrapRoundTripperWithConfig(http.DefaultTransport, config.HTTPOutgoingConfig{HTTPPropagationHook: propagateTraceHook}),
		Timeout:   time.Second * 5,
	}
	res, err := client.Do(req)
	if err != nil {
		panic(err)
	}
	body, err := ioutil.ReadAll(res.Body)
	res.Body.Close()
	if err != nil {
		panic(err)
	}
	s := string(body)
	//fmt.Println(s)
	return string(s), ctx
}

func propagateTraceHook(r *http.Request, prop *propagation.PropagationContext) map[string]string {
	ctx := r.Context()
	ctx, headers := propagation.MarshalW3CTraceContext(ctx, prop)
	return headers
}
