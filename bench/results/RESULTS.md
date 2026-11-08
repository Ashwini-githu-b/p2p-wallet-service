# Wallet Service Benchmark Results

- **Date**: 2026-11-08 15:42:27 UTC
- **Git Commit**: `a438fab`
- **OS / Arch**: `Darwin arm64`
- **CPU**: Apple M1
- **RAM**: 8 GB
- **Docker Limits**: Default host limits
- **k6 Version**: `k6 v2.3.0 (commit/e088784614, go1.27.1, linux/arm64)`
- **Parameters**: 50 VUs, 30s measure duration, 200 initial funded users

> **Note**: Results are measured on a single local machine with PostgreSQL running in Docker.

| Strategy | Scenario | Throughput (req/s) | p50 ms | p95 ms | p99 ms | 201 | 422 | other | Reconciliation |
|---|---|---|---|---|---|---|---|---|---|
| ORDERED_LOCKS | uniform | NOT RUN YET | - | - | - | - | - | - | - |
| ORDERED_LOCKS | hot_wallet | NOT RUN YET | - | - | - | - | - | - | - |
| ORDERED_LOCKS | pingpong | NOT RUN YET | - | - | - | - | - | - | - |
| ATOMIC_UPDATE | uniform | NOT RUN YET | - | - | - | - | - | - | - |
| ATOMIC_UPDATE | hot_wallet | NOT RUN YET | - | - | - | - | - | - | - |
| ATOMIC_UPDATE | pingpong | NOT RUN YET | - | - | - | - | - | - | - |
| OPTIMISTIC | uniform | NOT RUN YET | - | - | - | - | - | - | - |
| OPTIMISTIC | hot_wallet | NOT RUN YET | - | - | - | - | - | - | - |
| OPTIMISTIC | pingpong | NOT RUN YET | - | - | - | - | - | - | - |

### Observations

NOT RUN YET
