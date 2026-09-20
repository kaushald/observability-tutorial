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
