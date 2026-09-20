# Food Delivery Tutorial: Codespaces-First Plan (v2)

This replaces the approach in `docs/v1/plan.md` and `docs/v1/FOOD_DELIVERY_PLAN.md`. The domain and the
teaching arc stay. The size of the system shrinks until it fits the one constraint that
cannot move: a student on a free GitHub account opens a Codespace and has a running,
traced app within a few minutes.

## Goals and constraints

1. Students recognise the app. Ordering food is familiar, and the failures (stuck order,
   slow checkout, declined card) need no explanation.
2. It runs on the free 2-core Codespaces machine with no Docker, no external services and
   no toolchains beyond what the current devcontainer already installs.
3. It is fast at every step a student waits on:

   | Step | Budget |
   |---|---|
   | Codespace ready, with prebuild | under 1 minute |
   | Codespace ready, without prebuild | under 5 minutes, build runs while students get their API key |
   | Start a lesson (`./run.sh`) | under 20 seconds |
   | First trace visible after placing an order | under 10 seconds |
   | Memory for all services | under 1 GB |

4. Every demo the jokes lessons support today still has a lesson.
5. The whole thing fits a 2-hour session.

## What changed from v1, and why

| v1 | v2 | Reason |
|---|---|---|
| 4 services in Java, Go, Python, Node | 3 services in Java and Go | The current devcontainer already has Java 17 and Go. Python and Node add install time and two more runtimes to debug on stage. |
| Node web UI on its own port | Static page served by the order service | One forwarded port. Codespaces gives each port its own private URL, so a browser calling a second port fails. |
| Honeycomb distributions in lessons 001-006 | Upstream OpenTelemetry everywhere | Honeycomb archived all four distributions on 2025-08-12. The jokes lessons already use the upstream Java agent. |
| Lesson 007: migrate from distribution to standard OTel | Dropped | Nothing to migrate from. |
| SQLite, Redis in lesson 006 | H2 in-memory | No files, no server, no Hibernate dialect problem. The Java agent traces JDBC with no code. |
| 8 lessons, each a full copy of 4 services | 7 lessons, only the order service copied per lesson | Same copy count as today (6 Java copies). Go services exist once. |
| Random chaos middleware | Deterministic, data-driven faults | A live demo has to fail the same way every time. |
| Queue, dead letter queue, notification service | One background thread | Same teaching point as today's `003-async`, no new infrastructure. |

## The app

```
Browser ──▶ order-service (Java, :8080) ──▶ kitchen-service (Go, :8081)
             │  serves the UI             └─▶ delivery-service (Go, :8082)
             └─ H2 in-memory (menu, orders)
```

**order-service** (Spring Boot 3, Java 17, Spring Data JPA, H2). Serves a single static
page (restaurant list, menu, "Place order", order status) from `resources/static`. Owns
menus, pricing, a fake payment step and the order record. This is the only service students
edit, so all manual instrumentation is in one language.

**kitchen-service** (Go, `net/http`). Accepts a ticket, sleeps for the restaurant's prep
time, returns. In-memory.

**delivery-service** (Go, `net/http`). Assigns a driver from an in-memory pool, returns an
ETA.

Only port 8080 is opened in the browser. Ports 8081 and 8082 stay inside the Codespace.

### Built-in problems

The faults ship with the app from lesson 000. They are driven by data, so they reproduce
on demand.

| Problem | Trigger | Where students meet it |
|---|---|---|
| Kitchen timeout: prep takes 2.5s, the order service gives up at 2s | Any order from "Slow Noodles" | 000 (mystery), 001 (solved in one trace) |
| Business error, not a system error | Card number ending `0000` is declined | 002 |
| Lost trace context | Confirmation is sent on a background thread | 003 |
| N+1 query: one query per menu item | Loading any menu, obvious under load | 006 |

## Lessons

The teaching story for each lesson (the question it asks, what students do, what they take
away) is in `lesson-plan.md`. This section is the summary.

Each lesson directory holds finished code, like today. Students run it, look at the traces,
and make one small change. Times add up to 110 minutes.

| Lesson | Min | What students see | Replaces |
|---|---|---|---|
| `000-baseline` | 15 | Orders from one restaurant fail. Three log streams, no way to connect them. | `001-basic` |
| `001-auto` | 15 | Same code, agent switched on. One trace crosses all three services and the H2 queries. The timeout from 000 is visible at a glance. | `006-cross-service` |
| `002-spans` | 20 | Manual spans (`validate-order`, `calculate-price`), `@WithSpan`, attributes (`order.id`, `restaurant.id`, `order.total`). Group by `restaurant.id` in Honeycomb. Declined card sets error status. | `002-spans` |
| `003-async` | 15 | Confirmation thread shows up as an orphan trace. Fix with `Context.current().wrap`. | `003-async` |
| `004-events` | 10 | Order state changes as span events (`payment.authorized`, `kitchen.accepted`), `recordException`. | `004-events` |
| `005-links` | 15 | `POST /orders/{id}/refund` starts a new trace linked to the original order. The order row stores its trace and span IDs. | `005-links` |
| `006-investigate` | 20 | `scripts/lunch-rush.sh` sends load. Students find the N+1 query and the slow restaurant from traces alone, then compare with the fixed version. | new |

`000-baseline` has no source of its own. Its `run.sh` starts the lesson 001 build without
the agent and with Go tracing off. That the only difference is a flag is the point of
lesson 001.

Left for a take-home appendix: sampling, an OTel Collector, metrics and logs.

## Repository layout

Everything for this track lives under `advanced/`. The jokes lessons stay at the repository
root as the basic track. `.devcontainer/`, `.github/`, `lib/` and `.env` stay at the root
because GitHub requires the first two there and the basic lessons share the other two.

```
.devcontainer/{devcontainer.json, install-go.sh}
lib/opentelemetry-javaagent.jar   # vendored and pinned, shared with the basic lessons
001-basic/ ... 006-cross-service/ # basic track (jokes), unchanged
advanced/
  settings.gradle          # one Gradle build, one wrapper, all lessons as subprojects
  gradlew, gradle/
  services/
    kitchen-service/       # Go, one copy
    delivery-service/      # Go, one copy
    loadgen/               # Go, used by lunch-rush.sh
  000-baseline/run.sh
  001-auto/{order-service/, run.sh, README.md}
  002-spans/ ... 006-investigate/   # same shape
  scripts/{env.sh, stop.sh, doctor.sh, lunch-rush.sh, smoke.sh}
  docs/                    # measurements, JVM comparison, the superseded v1 plan
```

A single root Gradle build fixes the problem PR #3 had to patch in six places (a wrapper
per lesson), and lets one command build every lesson jar.

## How it stays fast

**Codespace creation.** Keep the current image (`mcr.microsoft.com/devcontainers/java:17`
plus the Go feature). Add `forwardPorts: [8080]` and an `updateContentCommand` that runs
`./gradlew bootJar` for all lessons and `go build` for the three Go programs. With a
Codespaces prebuild configured on the repo, that work is done before the student arrives.
Without one, it runs once in the background while students create their Honeycomb key.

**Lesson start.** `run.sh` starts prebuilt artifacts: `java -Xmx256m -javaagent:... -jar`
and two Go binaries. It rebuilds only if sources changed, which Gradle does incrementally.

**Editor weight.** The Java extension pack's language server uses more memory and CPU than
the whole app. Leave it out of the devcontainer's extension list. Students change a few
lines per lesson and the README shows the exact code.

**Rough memory budget.** JVM with agent about 350 MB, each Go service about 15 MB.

## OpenTelemetry setup

- Java: upstream agent 2.x from `lib/`. Manual code uses `opentelemetry-api` and
  `opentelemetry-instrumentation-annotations`, as the jokes lessons do now.
- Go: `otelhttp` handlers and clients with the OTLP/HTTP exporter, switched on by
  `TRACING=on` so lesson 000 can run the same binaries untraced. This replaces `beeline-go`.
- `scripts/env.sh` is the one place that sets `OTEL_EXPORTER_OTLP_ENDPOINT`,
  `OTEL_EXPORTER_OTLP_HEADERS` and `OTEL_METRICS_EXPORTER=none`. It reads
  `HONEYCOMB_API_KEY` from a Codespaces secret if present, otherwise from `.env`.
- Everything is plain OTLP, so pointing the tutorial at another backend is a one-line
  change in `env.sh`.

## Scripts

- `<lesson>/run.sh`: starts the three processes, prefixes each log line with the service
  name, stops everything on Ctrl+C.
- `scripts/stop.sh`: kills anything left on 8080-8082.
- `scripts/doctor.sh`: checks Java, Go, the API key and free ports, and sends one test span.
- `scripts/lunch-rush.sh`: about 5 requests per second for 60 seconds.
- `scripts/smoke.sh <lesson>`: starts a lesson with `OTEL_TRACES_EXPORTER=console`, places
  an order, and checks the expected span names appear. Runs in GitHub Actions for every
  lesson so a broken lesson is caught before a session, not during one.

## Build order

Tests come first in each step: JUnit for the order flow, Go tests for the two services,
`smoke.sh` for each lesson.

1. **Walking skeleton.** Three services, the static page, `001-auto/run.sh`, devcontainer
   changes. Measure the budgets above in a Codespace on a free account. Also confirm
   whether a student's fork picks up the parent repo's prebuild. If the numbers miss,
   change the design here, before any lesson content exists.
2. **Faults and lesson 000.** Seed data, the four built-in problems, the baseline story.
3. **Lessons 002-005.** Mostly ports of the existing `JokeController` demos into the order
   flow.
4. **Lesson 006.** Load generator, the fixed variant, Honeycomb queries for the README.
5. **Dry run and cutover.** Full run-through on a fresh free account with a timer. The
   jokes lessons stay where they are as the basic track; the root README gets a section
   for each track.

## Decisions

Confirmed on 2026-09-20.

1. The session is about 2 hours, instructor-led, with students following along.
2. Honeycomb stays as the backend, and students sign up for a free account.
3. Java and Go are enough. A Python service would add a zero-code instrumentation demo in
   a third language, at the cost of a slower Codespace and one more runtime.
4. Lesson directories hold finished code. Students make small edits but do not write the
   instrumentation from scratch.
5. A Codespaces prebuild on the repo is acceptable. Its storage counts against the repo
   owner's quota, not the students'.
