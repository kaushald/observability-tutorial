# Lesson plan: advanced track

The technical plan is in `plan-v2.md`. This file is the teaching story: what each lesson
asks, what students do, and what they should walk away with.

One rule shapes the sequence: each lesson starts from a question the previous lesson's
traces could not answer.

| Lesson | Min | Question | Concept | Status |
|---|---|---|---|---|
| `000-baseline` | 15 | Why do some orders fail? | Logs alone, no correlation | built |
| `001-auto` | 15 | Which service is at fault? | Auto-instrumentation, context propagation | built |
| `002-spans` | 20 | Is it always that restaurant? | Attributes, manual spans, error status | built |
| `003-async` | 15 | Did the customer get their confirmation? | Context across threads | built |
| `004-events` | 10 | What happened to this order, and when? | Span events, recorded exceptions | built |
| `005-links` | 15 | Which order was this refund for? | Span links | built |
| `006-investigate` | 20 | Why is the lunch rush slow? | Using traces to find and fix problems | built |

Total: 110 minutes, leaving 10 minutes of slack in a 2-hour session.

## The app and its planted problems

An order service (Java) calls a kitchen service and a delivery service (both Go). Students
only ever edit the order service. The problems below are in the app from lesson 000 and
are driven by data, so they reproduce on demand.

| Problem | How to trigger it | Lesson that uses it |
|---|---|---|
| Kitchen takes 2.5 s, the order service gives up at 2 s | Order from Slow Noodles | 000, 001, 002, 004, 005 |
| Payment is authorized before the kitchen call, so a failed order has still charged the customer | Same order | 004, 005 |
| Card declined, a business outcome and not a fault | Card number ending `0000` | 002 |
| Confirmation sent from a raw thread, so its spans land in a separate trace | Any confirmed order | 001 (noticed), 003 (fixed) |
| Payment step takes 80 ms and makes no network or database call | Any order | 001 (a gap), 002 (explained) |
| Menu tags load with one query per item, ten queries per menu | Open any menu | 001 (noticed), 006 (fixed) |

## 000-baseline: why do some orders fail?

**Setup.** `cd advanced/000-baseline && ./run.sh`. No API key needed. Same build as lesson
001 with the agent off and Go tracing off.

**Students do.** Place a few orders from each restaurant in the browser. Orders from one
restaurant fail with "Order could not be completed". Using only the three log streams in
the terminal, work out which service is at fault and why. Five minutes.

**What they find.** The order service logs `Order 3 failed`. The kitchen logs
`ticket T-3 accepted` and, later, `ticket T-3 ready`. Nothing connects the lines, nothing
records a duration, and the kitchen never reports a problem because from its side there
was none.

**Takeaway.** Logs from separate services do not add up to an explanation. You need
something that follows one request across all of them.

**Instructor notes.** Let students struggle for the full five minutes. Ask who suspects
which service; expect a split. Do not reveal the answer: lesson 001 does that.

## 001-auto: which service is at fault?

**Setup.** `cd advanced/001-auto && ./run.sh`, with the Honeycomb key in `.env` or a
Codespaces secret. No code change from lesson 000: `run.sh` adds the OpenTelemetry Java
agent and sets `TRACING=on` for the Go services.

**Students do.** Place the same orders, then open Honeycomb and look at a Bella Pizza
trace and a Slow Noodles trace.

**What they see.** One trace per order across all three services, including the SQL the
order service ran. In the failing trace, the kitchen's `POST /tickets` span runs for 2.5 s
under an order service call that ended at 2 s. The mystery from lesson 000 is solved by
looking.

**Takeaway.** Auto-instrumentation covers the edges of a service with no code: HTTP in,
HTTP out, database calls, and the `traceparent` header that carries context between
services.

**Loose ends to point out.** These are the hooks for the next lessons.

1. A stray trace that contains only `POST /notifications`.
2. An 80 ms gap inside every order trace where no span appears.
3. Nothing in the trace says which restaurant or which order it was.
4. A menu load runs ten SQL queries.

## 002-spans: is it always that restaurant?

**The question.** How many orders fail, and do they all come from one restaurant? The
auto trace cannot say: the restaurant ID is in the request body, which the agent does not
read.

**Students do.**

- Add attributes to the current span: `order.id`, `restaurant.id`, `order.total_cents`,
  `order.item_count`. In Honeycomb, group the 504 responses by `restaurant.id`. One
  restaurant accounts for all of them.
- Wrap `validate-order`, `calculate-price` and `authorize-payment` in spans, once with
  `@WithSpan` and once with the tracer API. The 80 ms gap from lesson 001 turns out to be
  the payment step.
- Set span status ERROR on the kitchen timeout. For a declined card, add a
  `payment.declined` attribute and leave the status alone.

**Takeaway.** Attributes turn traces into something you can query. Manual spans show work
the agent cannot see. A business outcome is not an error: marking declined cards as errors
would pollute every error-rate query.

## 003-async: did the customer get their confirmation?

**The question.** The order trace ends with no confirmation in it, and the stray
`POST /notifications` traces have no parent. Which order does each belong to?

**Why it happens.** Trace context lives in a thread-local. The agent carries it across
executors, but the app sends the confirmation from a raw `new Thread`, which the agent
does not cover.

**Students do.** Wrap the runnable: `Context.current().wrap(runnable)`. Place an order and
look at the trace again.

**What they see.** The confirmation span is now inside the order trace and extends past
the end of the HTTP response.

**Takeaway.** Context does not cross thread boundaries by itself everywhere. When a trace
is missing a piece, look for a hand-rolled thread, queue or callback. Work that outlives
the request still belongs to the request's trace.

## 004-events: what happened to this order, and when?

**The question.** Spans describe work with a duration. Some facts are moments: the payment
was authorized, the kitchen accepted the ticket.

**Students do.** Add span events on the order span: `order.validated`,
`payment.authorized` or `payment.declined`, `kitchen.accepted`, `driver.assigned`, each
with a few attributes. Call `recordException` on the kitchen timeout.

**What they see.** A timeline inside the span. In the Slow Noodles trace,
`payment.authorized` is followed by a timeout, with the exception and its stack trace
attached. That is the detail the lesson 000 log line left out. It also shows that the
customer was charged for an order that failed.

**Takeaway.** A rule for choosing: a span for work you want timed, an event for a moment,
an attribute for a fact about the whole operation.

## 005-links: which order was this refund for?

**The question.** Every failed Slow Noodles order charged the customer, so those orders
need refunds. A refund happens minutes or days later in its own request. Making it a child
of the order trace would be wrong, because that operation ended long ago.

**Students do.** Work with a new endpoint, `POST /api/orders/{id}/refund`. The order row
stores its trace ID and span ID when the order is created. Students add a link from the
refund span to that span context, then follow the link in Honeycomb from the refund trace
to the original order.

**Takeaway.** Parent and child means "caused by, within one operation". A link means
"related to, across operations". Batch jobs, retries and follow-up workflows are the usual
places for links.

## 006-investigate: why is the lunch rush slow?

**Setup.** `advanced/scripts/lunch-rush.sh` sends about 5 requests per second for a minute.

**Students do.** Run lesson 005 under load and, with no hints, find the two things that
hurt most using only traces: the slow restaurant, and the menu endpoint that runs one query
per item. Then run `006-investigate`, which holds the fixed menu query, send the same load,
and compare. Slow Noodles stays broken on purpose: the order service cannot fix the kitchen,
and the options (longer timeout, charge after the kitchen accepts) make a good closing
discussion.

**Takeaway.** The same instrumentation that explained one failed order also shows where
the system spends its time. Optimise what the traces show, not what you guess.

## Threads that run through the lessons

- **Slow Noodles:** a mystery in 000, located in 001, quantified in 002, recorded as an
  exception in 004, refunded in 005, found again under load in 006.
- **The confirmation thread:** noticed in 001, fixed in 003.
- **The ten-query menu:** noticed in 001, left alone until students find it themselves in 006.

## Mapping to the basic track

| Basic (jokes) | Advanced |
|---|---|
| `001-basic` | `000-baseline` |
| `002-spans` | `002-spans` |
| `003-async` | `003-async` |
| `004-events` | `004-events` |
| `005-links` | `005-links` |
| `006-cross-service` | `001-auto` (cross-service tracing arrives in the first traced lesson) |
