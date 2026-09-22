# 006: Why is the lunch rush slow?

No new instrumentation in this lesson. You use what lessons 001 to 005 added to find out
where the system spends its time under load, fix one thing, and compare.

## Part 1: investigate (run lesson 005)

Start the previous lesson and send it a minute of lunch-rush traffic from a second terminal:

```
cd advanced/005-links
./run.sh
```

```
advanced/scripts/lunch-rush.sh
```

The load generator prints a table when it finishes: requests per endpoint, status codes,
and p50, p95 and max latency. Note the numbers for `GET /api/restaurants/{id}/menu` and
`POST /api/orders`.

Now use Honeycomb, with no hints from the code, to answer:

1. Which requests fail, and what do they have in common? Try grouping `POST /api/orders` by
   `http.response.status_code` and `restaurant.id`, and a heatmap of `duration_ms`.
2. Open one menu trace. How many database spans does it have, and what do they look like?
   Try counting spans where `db.system` exists, grouped by `trace.trace_id`.

You should find two things: every order from restaurant 2 runs into the 2 second timeout,
and every menu load runs one query for the items plus one query per item for its tags.

## Part 2: compare with the fix (run lesson 006)

This lesson fixes the second problem. `MenuItemRepository.findByRestaurantId` now has
`@EntityGraph(attributePaths = "tags")`, so the items and their tags come back in one
query. `scripts/lesson-diff.sh 006-investigate` shows the whole change.

```
cd advanced/006-investigate
./run.sh
```

Run `advanced/scripts/lunch-rush.sh` again and compare the menu numbers and a menu trace
with Part 1.

With an in-memory database each query takes well under a millisecond, so the latency gain
here is small. Against a database across a network, ten round trips per menu instead of two
is the difference students would feel. The shape in the trace is what to remember: a
staircase of identical short spans under one request almost always means a query in a loop.

## What is not fixed

Slow Noodles still times out. The order service cannot make the kitchen faster. Options to
discuss: a longer timeout, authorizing payment only after the kitchen accepts, or showing
the customer "preparing" and confirming later. The traces from lesson 004 are the evidence
for that conversation.

## Takeaway

The same instrumentation that explained one failed order also shows where the system
spends its time. Optimise what the traces show, not what you guess.
