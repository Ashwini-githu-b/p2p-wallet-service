# Wallet Service Benchmark Harness

An automated load testing and benchmarking suite using k6 to evaluate concurrency strategies across distinct workload distributions.

## Methodology

### Scenarios
1. **`uniform`**:
   - Random senders and receivers drawn uniformly across all pre-funded accounts.
   - Transfer amounts randomly distributed between 100 and 10,000 paise (₹1.00 – ₹100.00).
   - Evaluates general ledger throughput under dispersed concurrency with minimal row-level contention.

2. **`hot_wallet`**:
   - Random senders transferring funds into a single, high-traffic receiver account (`hotUser`).
   - Transfer amounts between 100 and 1,000 paise (₹1.00 – ₹10.00).
   - Stresses the database on high write contention for a single credit balance record.

3. **`pingpong`**:
   - Two users (`ids[0]` and `ids[1]`) continuously transferring ₹1.00 back and forth.
   - Direction alternates based on iteration parity (`A -> B` on even, `B -> A` on odd).
   - Designed to expose deadlock potential in bidirectional transfers and measure latency when two accounts are perpetually locked.

### Invariants & Auto-Reconciliation
- Every request includes a strictly unique `Idempotency-Key`.
- Each scenario runs a `warmup` phase (default 10s) followed by a `measure` phase (default 30s) under constant virtual users (default 50 VUs).
- Immediately following each scenario completion, an automated reconciliation audit is invoked via `POST /api/v1/admin/reconciliation/run`. The orchestrator asserts that reconciliation status is `OK` with zero discrepancies (`discrepancyCount == 0` and `totalBalancePaise == 0`). Any deviation fails the benchmark run immediately.

## Running Benchmarks

### Prerequisites
- Docker & Docker Compose
- Either native `k6` CLI or Docker (the script automatically invokes `grafana/k6` in a container if `k6` is not in `PATH`).

### Execution
Run all strategies and scenarios with default settings:
```bash
./bench/run.sh
```

Customizing parameters:
```bash
VUS=50 DURATION=30s WARMUP=10s USERS=200 ./bench/run.sh
```

To summarize existing JSON outputs:
```bash
python3 bench/summarize.py
```
Summary tables are written to `bench/results/RESULTS.md`.

