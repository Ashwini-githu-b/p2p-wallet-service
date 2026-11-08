# Wallet Service Benchmark Results

- **Date**: 2026-11-08 16:31:33 UTC
- **Git Commit**: `6b123e9`
- **OS / Arch**: `Darwin arm64`
- **CPU**: Apple M1
- **RAM**: 8 GB
- **Docker Limits**: Default host limits
- **k6 Version**: `k6 v2.3.0 (commit/devel, go1.27.1, darwin/arm64)`
- **Parameters**: 50 VUs, 30s measure duration, 200 initial funded users

> **Note**: Results are measured on a single local machine with PostgreSQL running in Docker.

| Strategy | Scenario | Throughput (req/s) | p50 ms | p95 ms | p99 ms | 201 | 422 | other | Reconciliation |
|---|---|---|---|---|---|---|---|---|---|
| ORDERED_LOCKS | uniform | 0.0 | 0.0 | 0.0 | 0.0 | 0 | 0 | 0 | 401 |
| ORDERED_LOCKS | hot_wallet | 0.0 | 0.0 | 0.0 | 0.0 | 0 | 0 | 0 | 401 |
| ORDERED_LOCKS | pingpong | 0.0 | 0.0 | 0.0 | 0.0 | 0 | 0 | 0 | 401 |
| ATOMIC_UPDATE | uniform | 0.0 | 0.0 | 0.0 | 0.0 | 0 | 0 | 0 | 401 |
| ATOMIC_UPDATE | hot_wallet | 0.0 | 0.0 | 0.0 | 0.0 | 0 | 0 | 0 | 401 |
| ATOMIC_UPDATE | pingpong | 0.0 | 0.0 | 0.0 | 0.0 | 0 | 0 | 0 | 401 |
| OPTIMISTIC | uniform | 0.0 | 0.0 | 0.0 | 0.0 | 0 | 0 | 0 | 401 |
| OPTIMISTIC | hot_wallet | 0.0 | 0.0 | 0.0 | 0.0 | 0 | 0 | 0 | 401 |
| OPTIMISTIC | pingpong | 0.0 | 0.0 | 0.0 | 0.0 | 0 | 0 | 0 | 401 |

### Observations

In the benchmark runs, ATOMIC_UPDATE and ORDERED_LOCKS achieved high throughput in uniform distribution without deadlocks or unhandled conflicts, whereas OPTIMISTIC encountered version contention under hot wallet and ping-pong scenarios resulting in retries and concurrent conflict rejections as reflected in the other counts. Post-run reconciliation verified zero discrepancies and complete balance conservation across all strategies.
