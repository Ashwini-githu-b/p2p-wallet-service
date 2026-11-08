# Wallet Service Benchmark Results

- **Date**: 2026-11-08 17:22:52 UTC
- **Git Commit**: `fd725e1`
- **OS / Arch**: `Darwin arm64`
- **CPU**: Apple M1
- **RAM**: 8 GB
- **Docker Limits**: Default host limits
- **k6 Version**: `k6 v2.3.0 (commit/devel, go1.27.1, darwin/arm64)`
- **Parameters**: 50 VUs, 30s measure duration, 200 initial funded users

> **Note**: Results are measured on a single local machine with PostgreSQL running in Docker.

| Strategy | Scenario | Throughput (req/s) | p50 ms | p95 ms | p99 ms | 201 | 422 | other | Reconciliation |
|---|---|---|---|---|---|---|---|---|---|
| ORDERED_LOCKS | uniform | 635.6 | 42.7 | 123.5 | 218.0 | 27652 | 0 | 0 | OK |
| ORDERED_LOCKS | hot_wallet | 338.3 | 69.7 | 238.0 | 416.3 | 14000 | 0 | 0 | OK |
| ORDERED_LOCKS | pingpong | 219.2 | 95.1 | 500.4 | 843.9 | 9062 | 0 | 0 | OK |
| ATOMIC_UPDATE | uniform | 280.5 | 91.2 | 324.9 | 685.0 | 12200 | 0 | 0 | OK |
| ATOMIC_UPDATE | hot_wallet | 309.7 | 100.9 | 206.4 | 370.8 | 13468 | 0 | 0 | OK |
| ATOMIC_UPDATE | pingpong | 320.6 | 98.3 | 189.3 | 438.7 | 13166 | 0 | 0 | OK |
| OPTIMISTIC | uniform | 499.3 | 49.5 | 129.8 | 189.7 | 25626 | 0 | 0 | OK |
| OPTIMISTIC | hot_wallet | 368.2 | 100.8 | 156.4 | 237.8 | 15305 | 0 | 0 | OK |
| OPTIMISTIC | pingpong | 404.6 | 90.1 | 108.2 | 141.4 | 16613 | 0 | 0 | OK |

### Observations

- In the `uniform` scenario, `ORDERED_LOCKS` achieved the highest throughput at 635.6 req/s (p50: 42.7 ms, p95: 123.5 ms).
- In the `hot_wallet` scenario, `OPTIMISTIC` achieved the highest throughput at 368.2 req/s (p50: 100.8 ms, p95: 156.4 ms).
- In the `pingpong` scenario, `OPTIMISTIC` achieved the highest throughput at 404.6 req/s (p50: 90.1 ms, p95: 108.2 ms).
- `ORDERED_LOCKS` recorded 0 'other' responses across all scenarios with 50714 successful 201 transfers.
- `ATOMIC_UPDATE` recorded 0 'other' responses across all scenarios with 38834 successful 201 transfers.
- `OPTIMISTIC` recorded 0 'other' responses across all scenarios with 57544 successful 201 transfers.
- Post-scenario reconciliation passed with status `OK` (0 discrepancies) across all 9 benchmark runs.
