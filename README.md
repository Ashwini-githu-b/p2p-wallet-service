# P2P Wallet Service

![CI](https://github.com/<owner>/<repo>/actions/workflows/ci.yml/badge.svg)

> **Live Demo**: `https://<your-render-url>`  
> *(Note: Free tier instances spin down after inactivity and may cold-start on the first request).*

<!-- TODO: add demo GIF of the Lab page -->

A high-performance, concurrency-safe Peer-to-Peer (P2P) digital wallet service built with Spring Boot 3.3.5, Java 21, and PostgreSQL 16.

---

## 1. Overview

The **P2P Wallet Service** provides an audit-compliant, robust balance and ledger management engine supporting user onboarding, wallet top-ups, peer-to-peer money transfers, background continuous reconciliation, multiple interchangeable concurrency control strategies, and a utilitarian operator console.

### Key Capabilities
- **Strict Double-Entry Bookkeeping**: Every money movement creates balanced debit and credit entries. The global sum of all wallet balances always equals zero.
- **Interchangeable Concurrency Strategies**: Switch dynamically between `ORDERED_LOCKS`, `ATOMIC_UPDATE`, and `OPTIMISTIC` transfer strategies without code changes.
- **Continuous Ledger Reconciliation**: Background repeatable-read snapshot audits verifying zero-sum invariance, transaction shapes, and ledger-to-balance consistency with zero false positives.
- **Production Observability**: Full Micrometer instrumentation exposing Prometheus metrics and a provisioned Grafana monitoring dashboard.
- **Operator Web Console**: Internal banking-style ledger console with a live Concurrency Lab (`/lab`) for stress-testing money conservation.
- **OpenAPI & Swagger Documentation**: Complete API specification available at `/swagger-ui.html` and `/v3/api-docs`.

---

## 2. Core Guarantees & Ledger Design

### System Account & Global Zero-Sum Invariant
All top-ups are modeled as real two-sided transfers originated from a reserved **system wallet** (`00000000-0000-0000-0000-000000000002`) owned by a reserved **system user** (`00000000-0000-0000-0000-000000000001`).
- The system account is completely hidden from the public API; requesting its user or wallet returns `404 USER_NOT_FOUND`.
- The system wallet balance is negative, exactly matching minus the total funds in circulation across all user wallets.
- **Global Invariant**: The sum of balances across **all** wallets (including the system wallet) is strictly zero:
  $$\sum_{w \in \text{Wallets}} \text{balance}(w) = 0$$

### Balanced Double-Entry Records
- **Top-Up**: 1 DEBIT row on the system wallet + 1 CREDIT row on the user's wallet. (In public responses and history, the user-facing counterparty remains `null`).
- **Transfer**: 1 DEBIT row on the sender wallet + 1 CREDIT row on the recipient wallet.
- **Balance Audit Invariant**: At all times, any individual wallet's balance exactly matches its ledger credit sum minus its debit sum:
  $$\text{balance}(w) = \sum \text{CREDIT}(w) - \sum \text{DEBIT}(w)$$

### Why Failed Operations Are Not Stored
Operations that abort (such as insufficient funds, validation errors, or lock timeouts) trigger an immediate database transaction rollback. Only successfully committed operations generate transaction rows and ledger entries. This avoids phantom ledger bloat, ensures the ledger remains an immutable historical record of genuine money movements, and leaves idempotency keys unconsumed for safe client retries.

---

## 3. Concurrency Control Strategies

The application provides three pluggable money movement implementations via the configuration property `wallet.transfer.strategy` (environment variable `WALLET_TRANSFER_STRATEGY`):

```
+-----------------------------------------------------------------------------------+
| Strategy           | Concurrency Mechanism       | Key Trade-Off                  |
+-----------------------------------------------------------------------------------+
| ORDERED_LOCKS      | SELECT ... FOR UPDATE       | Default. Explicit, deadlock-   |
|                    | (Canonical UUID order)      | free, easiest to reason about. |
+-----------------------------------------------------------------------------------+
| ATOMIC_UPDATE      | Conditional SQL UPDATE with | Eliminates SELECT locks.       |
|                    | RETURNING balance_paise     | Requires rollback-only trick.  |
+-----------------------------------------------------------------------------------+
| OPTIMISTIC         | JPA @Version with jittered  | High throughput on disjoint    |
|                    | exponential backoff retry   | keys; contention on hot rows.  |
+-----------------------------------------------------------------------------------+
```

### 1. `ORDERED_LOCKS` (Default)
- **Mechanism**: Sorts `fromWalletId` and `toWalletId` by `UUID.compareTo()`. Acquires pessimistic write locks (`SELECT ... FOR UPDATE`) in deterministic order, re-checks the idempotency key after locking, verifies sufficient balance, updates balances, and flushes ledger entries.
- **Cost**: Holds row locks during transaction execution. Zero retries or deadlocks under concurrent opposing transfers.

### 2. `ATOMIC_UPDATE`
- **Mechanism**: Executes single-statement conditional SQL updates using `JdbcTemplate` without loading JPA entities:
  ```sql
  UPDATE wallets SET balance_paise = balance_paise - ?, updated_at = ?
   WHERE id = ? AND (is_system OR balance_paise >= ?)
  RETURNING balance_paise;
  ```
- **Order & Rollback-Only Replay Trick**: Statements are executed in sorted wallet-ID order. If the debit update returns no rows (insufficient funds), the strategy re-checks if a concurrent identical request just committed. If found, it marks `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` so any credit already applied in this transaction is cleanly rolled back, then returns the idempotent replay.

### 3. `OPTIMISTIC`
- **Mechanism**: Reads wallets using plain `findById` without locks, capturing JPA `@Version` values. Balance changes and version increments are verified upon flush.
- **Facade Retries**: Conflicts surface as `ObjectOptimisticLockingFailureException` or `CannotAcquireLockException`. `TransferFacade` retries up to `wallet.transfer.retry.max-retries` (default 10) with full-jitter exponential backoff before returning `409 CONCURRENT_UPDATE`. Note: The system wallet is a contention hotspot for top-ups under this strategy.

---

## 4. Ledger Reconciliation

### What It Checks
A background auditor validates three mathematical invariants inside a single snapshot:
1. **`WALLET_BALANCE_MISMATCH`**: Every wallet's `balance_paise` equals its ledger credit sum minus its debit sum.
2. **`LEDGER_IMBALANCE`**: Every transaction has exactly 1 DEBIT and 1 CREDIT entry equal to the transaction amount.
3. **`GLOBAL_SUM_NONZERO`**: The global balance sum across all wallets equals zero.

### Why REPEATABLE READ Snapshot Isolation
The auditor runs with `@Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)`. In `READ COMMITTED`, running audit queries while transfers are in flight would observe different moments in time, producing false-positive mismatch alerts. In `REPEATABLE READ`, all audit queries observe the exact same committed snapshot. The first statement `SELECT pg_try_advisory_xact_lock(7001)` prevents overlapping audit executions and fixes the snapshot.

### Verifying Invariants
- **UI Indicator**: The TopBar polls `GET /api/v1/admin/reconciliation/latest` every 30s (`Ledger: balanced · checked 2m ago`).
- **Lab Page**: The `/lab` page features a dedicated **Ledger health** panel displaying audit metrics and any discrepancy details.
- **Admin Endpoint**:
  ```bash
  # Trigger manual synchronous audit
  curl -i -X POST http://localhost:8080/api/v1/admin/reconciliation/run \
    -H "X-Admin-Token: local-admin-token"
  ```
- **Tamper Proofs**: Automated integration tests in `ReconciliationTest` intentionally corrupt wallet balances (Tamper A) and ledger amounts (Tamper B) via direct SQL, asserting that reconciliation immediately flags `MISMATCH`, and then restores clean `OK` status once corrected.

---

## 5. Benchmarks

### Methodology
The benchmark harness in `bench/` executes load tests using k6 against a containerized PostgreSQL instance across three workload profiles:
- **`uniform`**: Senders and receivers chosen uniformly at random (50 VUs, 100–10,000 paise).
- **`hot_wallet`**: Random senders transferring into a single receiver account (hot row write contention).
- **`pingpong`**: Two accounts transferring ₹1.00 back and forth to test opposing lock cycles.

Immediately after each scenario run, `bench/run.sh` triggers an automated reconciliation audit and asserts `status: OK`.

### How to Run
```bash
# Execute automated benchmark across all 3 strategies and 3 scenarios
./bench/run.sh
```

### Benchmark Results (`bench/results/RESULTS.md`)

```
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
```

> **Note**: Benchmark results are marked `NOT RUN YET` until executed in an active benchmark environment. Real numbers can be generated at any time by running `./bench/run.sh`.

---

## 6. Observability (Prometheus & Grafana)

The service exposes production metrics via Spring Boot Actuator and Micrometer.

### Starting the Observability Stack
```bash
docker compose --profile observability up -d
```
- **Prometheus**: `http://localhost:9090` (scrapes `http://app:8080/actuator/prometheus` every 5s)
- **Grafana**: `http://localhost:3001` (Anonymous Viewer enabled, admin / admin)

### Metric Dictionary
- `wallet.operations` (Counter): Total facade operations tagged by `type` (`TRANSFER`/`TOPUP`) and `outcome` (`success`, `replayed`, `insufficient_funds`, `invalid`, `conflict_exhausted`, `error`).
- `wallet.operation.duration` (Timer): Facade latency percentiles-histogram tagged by `type`, `strategy`, and `outcome`.
- `wallet.retries` (Counter): Strategy conflict retries tagged by `strategy`.
- `wallet.reconciliation.discrepancies` (Gauge): Number of discrepancies found in the latest audit run.
- `wallet.reconciliation.last.run.timestamp` (Gauge): Unix epoch seconds of the latest audit run.
- `hikaricp_connections_active` / `hikaricp_connections_pending`: Connection pool utilization.

### Grafana Dashboard Panels
1. **Throughput (transfers/s)**: Real-time successful transfer operations per second.
2. **Latency (p50 / p95 / p99)**: Millisecond latency percentiles for transfers.
3. **Outcomes**: Stacked rate of operation outcomes.
4. **Retries/s**: Rate of concurrent update retries per second.
5. **DB Pool**: Active vs pending HikariCP database connections.
6. **Ledger Health**: Color-coded discrepancy counter (green = 0, red > 0) and seconds since last audit.

---

## 7. Deployment & Configuration

### Single-Container Production Image (`Dockerfile.prod`)
A multi-stage build packages the React frontend static assets directly into Spring Boot's `src/main/resources/static`, producing a self-contained image that serves the UI, API, and Swagger docs from a single port:
```bash
docker build -f Dockerfile.prod -t wallet-service:prod .
docker run -p 8080:8080 -e DB_URL=jdbc:postgresql://... wallet-service:prod
```

### Render Blueprint (`render.yaml`)
Deployable directly to Render using `render.yaml` with an external PostgreSQL provider (e.g. Neon):
```yaml
services:
  - type: web
    name: wallet-service
    runtime: docker
    dockerfilePath: ./Dockerfile.prod
    healthCheckPath: /actuator/health
```

### Environment Variables
| Variable | Description | Default |
|---|---|---|
| `PORT` | HTTP server port | `8080` |
| `DB_URL` | JDBC database connection string | `jdbc:postgresql://localhost:5432/wallet` |
| `DB_USER` | Database username | `wallet` |
| `DB_PASSWORD` | Database password | `wallet` |
| `DB_POOL_SIZE` | HikariCP maximum pool size | `20` |
| `ADMIN_TOKEN` | Token required for admin reconciliation triggers | `""` (disabled) |
| `WALLET_TRANSFER_STRATEGY` | Transfer concurrency strategy (`ORDERED_LOCKS`, `ATOMIC_UPDATE`, `OPTIMISTIC`) | `ORDERED_LOCKS` |

*Neon connection string format: `jdbc:postgresql://<host>/<db>?sslmode=require`*

---

## 8. Web Console UI (`/frontend`)

An operator console designed like an internal banking ledger book (warm paper, hairline borders, dense monospace figures):
- **Directory**: Keeps local accounts in `localStorage`. Operators switch between accounts without authentication.
- **TopBar**: Live API status, "API docs" Swagger link, and real-time ledger health status indicator.
- **Quick Start (`/accounts/new`)**: One-click setup creating 3 demo accounts (Asha, Ravi, Meera) funded with ₹10,000 each.
- **Ledger**: Paginated chronological history with point-in-time balance snapshots.
- **Concurrency Lab (`/lab`)**: Live interactive testbed with an at-a-glance **Ledger health** panel and parallel batch transfer stress-testing.

To run locally in development:
```bash
cd frontend && npm install && npm run dev
# Serves UI at http://localhost:5173 with proxy to backend at http://localhost:8080
```

---

## 9. API Reference & Swagger

Interactive Swagger UI documentation is available at:
👉 **`http://localhost:8080/swagger-ui.html`** (or via Web Console top bar "API docs")

Raw OpenAPI v3 JSON spec: `http://localhost:8080/v3/api-docs`

Base Path: `/api/v1`

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/users` | Onboard new user and wallet |
| `GET` | `/api/v1/wallets/{userId}` | Read wallet balance |
| `POST` | `/api/v1/wallets/{userId}/topup` | Top up wallet balance (Header: `Idempotency-Key`) |
| `POST` | `/api/v1/transfers` | Transfer paise between users (Header: `Idempotency-Key`) |
| `GET` | `/api/v1/wallets/{userId}/transactions` | Paginated transaction ledger history |
| `GET` | `/api/v1/admin/reconciliation/latest` | Read latest reconciliation audit summary |
| `POST` | `/api/v1/admin/reconciliation/run` | Trigger manual audit (Header: `X-Admin-Token`) |
| `GET` | `/actuator/health` | Service health status |
| `GET` | `/actuator/prometheus` | Prometheus metrics scrape endpoint |

---

## 10. Running the Complete Stack

```bash
# Start Postgres, Spring Boot backend, and Nginx frontend console
docker compose up --build

# Open the Web Console
open http://localhost:3000

# Open Swagger API documentation
open http://localhost:8080/swagger-ui.html

# Open Prometheus & Grafana (observability profile)
docker compose --profile observability up -d
open http://localhost:3001
```

---

## 11. Known Limitations & Future Work

1. **System-Wallet Hot Row on Top-Ups**: Top-ups debit the single system wallet. Under heavy top-up concurrency (especially with `OPTIMISTIC`), this creates a write contention hotspot. In production, sharding the system account into $N$ internal debit accounts (`system-1`, `system-2`) would distribute write locks.
2. **Full-Scan Reconciliation**: Current audits perform full table scans on `wallets` and `wallet_transactions`. For multi-million row tables, windowed or incremental reconciliation (auditing time slices or recently updated accounts) is recommended.
3. **Global Idempotency Keys**: Keys are currently unique across the entire database. Production systems should scope idempotency keys per user (`user_id + idempotency_key`) with a TTL expiry (e.g. 24–48 hours) to prevent unbounded index growth.
4. **Offset Pagination**: Transaction history uses offset-based pagination (`page` and `size`). Under high insert rates, keyset pagination (cursor-based on `created_at, id`) avoids page drifting.
5. **Authentication**: The service operates as an internal ledger engine without caller authentication. Production deployment requires JWT / OAuth2 bearer token authorization and role-based access control (RBAC).
