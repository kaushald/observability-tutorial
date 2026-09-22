# 003: Did the customer get their confirmation?

In lessons 001 and 002 every confirmed order left behind a small stray trace holding only
`POST /notifications`. The order's own trace never showed a confirmation at all. Nothing
tells you which order a stray trace belongs to.

## Why it happens

Trace context lives in a thread-local. The OpenTelemetry agent carries it across executors,
`@Async` and similar, but `ConfirmationSender` starts a raw `new Thread(...)`. The new thread
starts with an empty context, so any span created there begins a new trace.

## What changed

Only `ConfirmationSender.java`:

1. The background work is wrapped in a `send-confirmation` span with an `order.id` attribute.
2. The runnable is wrapped with `Context.current().wrap(task)`. `Context.current()` is read on
   the request thread, and `wrap()` makes that context current again inside the new thread.

`TracingTests` checks that the `send-confirmation` span is a child of the span that placed
the order.

## Run it

```
cd advanced/003-async
./run.sh
```

Order from Bella Pizza and open the trace in Honeycomb.

## What to look for

1. `send-confirmation` and the delivery service's `POST /notifications` are now inside the
   order trace. The stray traces are gone.
2. The confirmation spans extend past the end of `POST /api/orders`. The response went back
   to the browser about 200 ms before the confirmation finished. Work that outlives the
   request still belongs to the request's trace.
3. Try it the other way: change the last line of `sendAsync` back to
   `new Thread(task, "confirmation-sender").start()`, run `./run.sh` again, and watch the
   confirmation fall out of the order trace.

When part of a trace is missing, look for a hand-rolled thread, queue or callback.
