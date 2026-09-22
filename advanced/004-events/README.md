# 004: What happened to this order, and when?

A span has a duration: it starts, some work happens, it ends. Some facts about an order
are not durations, they are moments: the payment was authorized, the kitchen accepted the
ticket. A span can't place a moment on its own timeline. An event can.

## What changed

Only `OrderService.java`, adding five span events:

1. `order.validated`, once the order's items are loaded, with `order.item_count`.
2. `payment.authorized`, with `payment.amount_cents`; or `payment.declined`, with
   `payment.reason` set to `card_declined`.
3. `kitchen.accepted`, once the kitchen call returns, with `kitchen.ticket_id` and
   `kitchen.prep_millis`.
4. `driver.assigned`, once the delivery call returns, with `delivery.driver` and
   `delivery.eta_minutes`.
5. `span.recordException(e)` in `fulfil-order`'s catch block, next to the existing
   `setStatus(ERROR, ...)`. `recordException` adds an `exception` event with the
   exception's type, message and stack trace. It doesn't touch the span's status, which is
   why both calls are there: one records what happened, the other marks the span as failed.

`order.validated` and `payment.authorized` (or `payment.declined`) land on whichever span
is current when `placeOrder` runs - the server span the agent created for the request.
`kitchen.accepted` and `driver.assigned` land on `fulfil-order` instead, because they run
inside that span's `try` block. Same method, two different spans, depending on which one
is current at the time.

## Run it

```
cd advanced/004-events
./run.sh
```

## What to look for

Order from Bella Pizza and open the trace in Honeycomb. The order's span now has a small
timeline of events: `order.validated`, `payment.authorized`, and, inside `fulfil-order`,
`kitchen.accepted` and `driver.assigned`. Expand each one to see its attributes.

Now order from Slow Noodles. The kitchen call times out, so `fulfil-order` ends with an
ERROR status. Before that, though, `payment.authorized` already fired. Expand the
`exception` event and you get the exception's type, message and full stack trace - the
detail lesson 000's log line `Order 3 failed` never had. The event order also makes a
second thing visible: the payment was authorized before the kitchen call failed, so this
order charged the customer for a delivery that never happened. Lesson 005 handles the
refund.

A rule of thumb for choosing among the three: a span for work you want timed, an event for
a moment, an attribute for a fact about the whole operation.
