# O11y Tutorial

## Signing up for Honeycomb

### Sign up for an Account

https://ui.honeycomb.io/signup

### Create a team

https://ui.honeycomb.io/teams

## Option 1 - Local Setup

This is required only if you plan to run locally. If you plan to use GitHub Codespaces, follow the instructions in the Codespaces section.

### JDK

Version 11 or greater JDK is required.

https://docs.oracle.com/en/java/javase/17/install/overview-jdk-installation.html

### Gradle

https://gradle.org/install/

### Go

Version 1.16 or greater is required.

Download and installation instructions can be found here:
https://golang.org/dl/

### Add API key to your environment

(Skip this step if you are not running locally)

```shell
# bash/zsh
export HONEYCOMB_API_KEY=<your-api-key>

# fish
set -gx HONEYCOMB_API_KEY <your-api-key>
```

## Option 2 - GitHub Codespaces

You can run this repository using [GitHub Codespaces](https://docs.github.com/en/codespaces).

1. Open the repository on GitHub.
2. Click the **Code** button and select **Open with Codespaces**.
3. If you don't have a Codespace already, create a new one.
4. Once your Codespace is ready, you'll have an environment pre-configured to run the examples.
5. Update the `HONEYCOMB_API_KEY` environment variable in the `.env` file with your Honeycomb API key you generated above.

## Two tracks

The repository holds two tutorials. They teach the same OpenTelemetry concepts and can be
taken independently.

| | Basic track | Advanced track |
|---|---|---|
| App | A jokes service (Java), plus a Go service in the last lesson | A food delivery app: an order service (Java) calling a kitchen service and a delivery service (both Go) |
| Where | `001-basic` to `006-cross-service` at the repository root | `advanced/000-baseline` to `advanced/006-investigate` |
| Start a lesson | `./bnd.sh` | `./run.sh` |
| Length | Short demos, one concept each | About 2 hours, each lesson answers a question the previous one raised |

## Basic track: jokes service

Navigate to the folders starting with 00\* at the repository root and run the bnd.sh script.

```shell
cd 001-basic
./bnd.sh

# To stop the services press Ctrl+C
```

## Advanced track: food delivery app

Some orders in this app fail, some confirmations go missing and the menu is slow under
load. Each lesson adds the instrumentation that explains one of those problems.

| Lesson | Question | Concept |
|---|---|---|
| `000-baseline` | Why do some orders fail? | Logs alone, no correlation |
| `001-auto` | Which service is at fault? | Auto-instrumentation, context propagation |
| `002-spans` | Is it always that restaurant? | Attributes, manual spans, error status |
| `003-async` | Did the customer get their confirmation? | Context across threads |
| `004-events` | What happened to this order, and when? | Span events, recorded exceptions |
| `005-links` | Which order was this refund for? | Span links |
| `006-investigate` | Why is the lunch rush slow? | Using traces to find and fix problems |

```shell
cd advanced/000-baseline   # no tracing, no API key needed
./run.sh

cd advanced/001-auto       # same code, traced by the OpenTelemetry Java agent
./run.sh

# To stop press Ctrl+C. If something is left running: advanced/scripts/stop.sh
```

Each lesson folder has a README with what changed and what to look for in Honeycomb.

In a Codespace everything is already built. To run locally you need JDK 17 or later and
Go 1.25; run `advanced/scripts/build.sh` once. If ports 8080-8082 are taken on your
machine, set `ORDER_PORT`, `KITCHEN_PORT` and `DELIVERY_PORT` before running.
`advanced/scripts/smoke.sh 001-auto` checks a lesson end to end without a Honeycomb key.
