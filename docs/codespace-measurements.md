# Codespace measurements

The plan in `plan-v2.md` only works if these numbers hold on a free account. Measure on
the default 2-core machine, from a GitHub account on the Free plan, with no prebuild
configured. Then configure a prebuild and repeat the first two rows.

| # | What | How | Budget | Measured |
|---|---|---|---|---|
| 1 | Codespace creation until the terminal is usable | Stopwatch from clicking "Create codespace" | 5 min without prebuild, 1 min with | |
| 2 | One-time build | `scripts/build.sh` prints "Build finished in Ns". It runs as `updateContentCommand`; see the creation log (Cmd/Ctrl+Shift+P, "Codespaces: View Creation Log") | included in row 1 | |
| 3 | Lesson start | `cd 001-auto && ./run.sh` prints "Ready in Ns" | 20 s | |
| 4 | Lesson start, no tracing | `cd 000-baseline && ./run.sh` | 20 s | |
| 5 | First trace visible | Place an order, stopwatch until it shows in Honeycomb | 10 s | |
| 6 | Memory | In a second terminal while a lesson runs: `scripts/measure.sh` | 1 GB total | |
| 7 | Machine | `nproc` and `free -g` | | |
| 8 | Works end to end without a key | `scripts/smoke.sh 001-auto` prints PASS | | |

Also check:

- The app opens from the forwarded port 8080 URL and orders work from the browser.
- Ports 8081 and 8082 do not pop up forwarding notifications.
- VS Code does not install the Java extension pack.
- A codespace created from a fork of the repo: does it use the parent repo's prebuild?

For comparison, time the current `002-spans` lesson the same way (`cd 002-spans && ./bnd.sh`).

## Results

### 2026-09-20, first attempt (commit a4f8dfa)

Codespace creation took more than 5 minutes. Phase timings from the prebuild workflow log
(run 35519685130), which performs the same steps on the same kind of machine:

| Phase | Time |
|---|---|
| Clone | 3 s |
| Pull `mcr.microsoft.com/devcontainers/java:17` | 40 s |
| Go devcontainer feature (downloads Go, compiles gopls, dlv, staticcheck) | 182 s |
| `build.sh`: Go services | 48 s |
| `build.sh`: Gradle distribution, dependencies, jar | 46 s |
| **Total a student waits without a prebuild** | **5 min 38 s** |
| Prebuild only: snapshot the disk | about 5 min |
| Prebuild only: upload to two East US locations | about 7 min |

Changes made in response:

- Dropped the Go feature. `.devcontainer/install-go.sh` installs only the toolchain (3 s in
  a local rehearsal). With no features left, Codespaces also skips the image build step.
- Replaced `autoexport` in the Go services with the two exporters actually used, which
  cut the cold Go build by about a quarter.
- `build.sh` runs the Go build and the Gradle build at the same time.
