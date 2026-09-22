# 005: Which order was this refund for?

Every Slow Noodles order that times out has still charged the customer: lesson 004 showed
`payment.authorized` firing before the kitchen call fails. Those orders need refunds. A
refund happens later, in its own request, minutes or days after the order's trace ended.
Making the refund a child of that trace would be wrong - there is no live parent span to
attach to, that operation is long over. Instead the refund's span carries a link to the
span that placed the original order.

## What changed

1. `CustomerOrder.java`: two nullable columns, `traceId` and `spanId`, and a new
   `REFUNDED` status.
2. `OrderService.placeOrder`: right after the order is first saved, while its span is
   still current, `Span.current().getSpanContext()` is read and, if valid, its trace ID
   and span ID are stored on the order. That's what lets the later, unrelated refund
   request point back at this one.
3. `PaymentService.refund`: a new `@WithSpan("refund-payment")` method that simulates
   ~60 ms of work, the same shape as `authorize-payment`.
4. `OrderService.refundOrder`: the new endpoint's logic.
   - An unknown order falls through to the same not-found handling as `getOrder`, so it
     is a 404.
   - Only an order with status `FAILED` can be refunded. Anything else - `CONFIRMED`,
     `DECLINED`, or an order that has already been refunded - is a 409 with
     `{"message":"Only failed orders can be refunded"}`.
   - It creates a span named `refund-order` with the tracer API, sets `order.id` on it,
     and, when the order has a stored trace and span ID, adds a link to that span context
     on the span **builder**, before the span starts - so a sampler deciding whether to
     keep this trace can see the link. The link carries `link.reason: original-order`.
   - Inside the span it calls `paymentService.refund(...)`, sets the order's status to
     `REFUNDED`, saves it, logs `Order 17 refunded`, and adds a `payment.refunded` event
     with `payment.amount_cents`.
5. `Controllers.java`: `POST /api/orders/{id}/refund`, plus a handler that turns the new
   "not refundable" exception into a 409.
6. `Api.java`: a `RefundResponse` record - `{"orderId":17,"status":"REFUNDED","refundedCents":2300}`.
7. `index.html`: a failed order's result now shows a "Request refund" button. Clicking it
   posts to the refund endpoint and shows "Refunded $23.00", or the error message if the
   order can't be refunded.

## Run it

```
cd advanced/005-links
./run.sh
```

## What to try

Order from Slow Noodles and wait for it to fail. Click "Request refund" - it succeeds and
shows the refunded amount. In Honeycomb, open the `refund-order` trace: it's a trace of
its own, with one span. Follow its link to the original order's trace, where you'll find
`payment.authorized` followed by the kitchen timeout - the charge that made the refund
necessary in the first place.

Try clicking refund again, or refunding a confirmed order: both give a 409, because only a
failed order that hasn't already been refunded qualifies.

Parent and child means "caused by, within one operation". A link means "related to, across
operations". Batch jobs, retries and follow-up workflows are the usual places you reach for
a link instead of a parent.
