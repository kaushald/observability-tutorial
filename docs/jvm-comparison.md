# JVM comparison for the order service

Question: would a JVM with a smaller footprint help on a free Codespace?

Measured on 2026-09-20 with `scripts/jvm-bench.sh`: the lesson 001 jar in a container
limited to 2 CPUs and 2 GB, OpenTelemetry Java agent 2.14.0 attached (except the first
row), a few dozen requests, then RSS of the java process. Host was an arm64 Mac, so
absolute numbers will differ on a Codespace; the ranking is what matters. Second of two runs:

| JVM and flags | Ready | Spring startup | RSS |
|---|---|---|---|
| HotSpot (Temurin 17), `-Xmx256m`, no agent | 2.2 s | 1.7 s | 299 MB |
| HotSpot, `-Xmx256m` | 3.7 s | 2.3 s | 375 MB |
| HotSpot, `+UseSerialGC` | 3.8 s | 2.3 s | 332 MB |
| HotSpot, `+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k` | 3.1 s | 2.0 s | 301 MB |
| OpenJ9 (IBM Semeru 17), `-Xmx256m` | 4.2 s | 2.4 s | 281 MB |
| OpenJ9, `-Xshareclasses -Xquickstart -Xtune:virtualized` | 3.7 s | 2.1 s | 308 MB |

## Decision

Stay on the HotSpot JDK that ships in the devcontainer image and use the tuned flags
(set in `scripts/env.sh`).

- OpenJ9 uses about 25% less memory than untuned HotSpot, but only about 20 MB less than
  tuned HotSpot, and it starts slower.
- Using OpenJ9 means installing a second JDK when the Codespace is created, which costs
  more time than the memory is worth. Memory is not the tight resource on a Codespace;
  CPU and creation time are.
- `TieredStopAtLevel=1` skips the C2 compiler. Peak throughput drops, which does not
  matter at workshop load, and startup gets faster because two cores are not spent on
  optimising code that runs for a few minutes.
- The agent costs about 1.5 s of startup and 50 to 75 MB.
- GraalVM native image was not measured: the Java agent cannot instrument a native
  binary, and lesson 001 depends on the agent.
