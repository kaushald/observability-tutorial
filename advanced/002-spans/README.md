# 002: Attributes, manual spans, and error status

The app is a small food delivery system:

```
Browser -> order-service (Java, :8080) -> kitchen-service (Go, :8081)
                                       -> delivery-service (Go, :8082)
```

## The question

Lesson 001 found the Slow Noodles timeout by looking at one trace. But is it always that
restaurant? How many orders fail, and where do they come from? The auto-instrumented
trace has no answer: the restaurant ID and order ID live in the request body, and the
agent does not read request bodies.

## Run it

Put your Honeycomb API key in `.env` at the repository root (or set a Codespaces secret
named `HONEYCOMB_API_KEY`), then:

```
cd advanced/002-spans
./run.sh
```

Open the URL it prints. Order from Bella Pizza, then Slow Noodles, then try a card
number ending in `0000` (the UI's default card number already ends in `4242`; change the
last four digits to `0000` to see a decline).

Press Ctrl+C to stop. If something is left running, use `advanced/scripts/stop.sh`.

## The four changes

All of them are in `order-service`, marked with a `// Lesson 002:` comment.

1. **Attributes on the current span** (`OrderService.java`). `order.id`, `restaurant.id`,
   `order.total_cents` and `order.item_count` are set on `Span.current()` as soon as each
   value is known: `restaurant.id` right after the order is validated, `order.item_count`
   once the items are loaded, `order.total_cents` once the price is calculated, and
   `order.id` once the order row is saved. With the agent running, "the current span" is
   the `POST /api/orders` span the agent already created, so these attributes land
   straight on it.

2. **A manual span with the tracer API** (`OrderService.java`, method `fulfilOrder`).
   The calls to the kitchen and delivery services are wrapped in a span named
   `fulfil-order`, created with `GlobalOpenTelemetry.getTracer("order-service")`. With the
   tracer API you own the span: you start it, make it current with `makeCurrent()` in
   try-with-resources, end it in `finally`, and set its status yourself. If the kitchen or
   delivery call fails, the span gets `StatusCode.ERROR` with the description
   `downstream call failed` before the exception is rethrown.

3. **Manual spans with the annotation** (`OrderService.java`, `PaymentService.java`).
   `@WithSpan("validate-order")` on order validation, `@WithSpan("calculate-price")` on
   the total calculation, `@WithSpan("authorize-payment")` on `PaymentService.authorize`.
   Unlike the tracer API, you write nothing about span lifecycle: the javaagent finds the
   annotation by bytecode and wraps the method call, including when the method calls
   itself from within the same class.

4. **Business error vs system error** (`OrderService.java`). A declined card sets
   `payment.declined=true` on the current span and leaves its status alone; an approved
   payment sets `payment.declined=false`. A timeout or connection failure to the kitchen
   or delivery service, by contrast, sets the `fulfil-order` span's status to ERROR. A
   declined card is a business outcome, not a fault in the system: marking it as an error
   would pollute every error-rate query with something that isn't actually broken.

The HTTP API itself is unchanged: same endpoints, same request and response bodies, same
status codes.

## What to try in Honeycomb

1. Place a mix of orders across all three restaurants, including a few from Slow Noodles.
   Group the 504 responses by `restaurant.id`. One restaurant accounts for all of them.
2. Find the `authorize-payment` span in any order's trace. It runs about 80ms and makes
   no network or database call. That is the gap lesson 001 pointed out but could not
   explain.
3. Compare a declined order (card ending `0000`) with a timed-out Slow Noodles order.
   Both are "failures" from the customer's point of view, but only the timeout shows up
   as an ERROR span; the decline shows up as `payment.declined=true` with no error status
   anywhere in the trace.

## @WithSpan versus the tracer API

`@WithSpan` is less code and is the right choice for "wrap this whole method in a span."
The tracer API is more code, but it is the only option when a span needs to end at a
different point than a method return, needs a name or attributes that depend on runtime
values gathered outside the method, or needs its status set based on what happened
inside it. `fulfil-order` needs to end in a `finally` and get an ERROR status on failure,
which is why it uses the tracer API instead of the annotation.
