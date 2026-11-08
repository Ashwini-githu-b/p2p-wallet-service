#!/usr/bin/env bash
# bench/run.sh — Wallet Service Benchmark Orchestrator
#
# k6 runs INSIDE the Docker Compose network (service: k6, profile: bench)
# hitting http://app:8080 directly — no macOS port-forwarding NAT.
# Health checks are also performed from inside the Docker network so the
# "ready" signal comes from the same path k6 will use, not the host NAT.
# Reconciliation curl calls go via localhost:8080 (host-side, infrequent).
#
# Usage:
#   ./bench/run.sh                        # run full matrix, app stays up
#   BENCH_RESET=1 ./bench/run.sh          # docker compose down -v first
#   USERS=50 WARMUP=3s DURATION=10s ./bench/run.sh  # override params

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results"

mkdir -p "$RESULTS_DIR"

ADMIN_TOKEN="${ADMIN_TOKEN:-local-admin-token}"
# HOST-side base URL (for curl reconciliation calls only)
HOST_BASE_URL="${HOST_BASE_URL:-http://localhost:8080}"
# In-network base URL for k6 container
K6_BASE_URL="http://app:8080"

BENCH_RESET="${BENCH_RESET:-0}"
USERS="${USERS:-200}"
VUS="${VUS:-50}"
DURATION="${DURATION:-30s}"
WARMUP="${WARMUP:-10s}"

STRATEGIES=("ORDERED_LOCKS" "ATOMIC_UPDATE" "OPTIMISTIC")
SCENARIOS=("uniform" "hot_wallet" "pingpong")

# ── Helpers ────────────────────────────────────────────────────────────────

# Wait until the app is reachable FROM INSIDE the Docker network.
# Uses a temporary k6 container (same image, same network) to wget the
# health endpoint — this is the only reliable signal that k6 itself
# will be able to reach the app.
wait_for_health_in_network() {
  local max_wait=180
  local elapsed=0
  echo "  → Waiting up to ${max_wait}s for app:8080 to be reachable inside Docker network..."
  while [ $elapsed -lt $max_wait ]; do
    # One-shot wget inside the bench-profile network
    local status
    status="$(docker compose --profile bench run --rm --no-deps \
      --entrypoint="" \
      -e "" \
      k6 \
      wget -qO- http://app:8080/actuator/health 2>/dev/null \
      | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('status',''))" 2>/dev/null \
      || echo "")"
    if [ "$status" = "UP" ]; then
      echo "  → App is UP and reachable inside Docker network (after ${elapsed}s)."
      return 0
    fi
    sleep 3
    elapsed=$((elapsed + 3))
  done
  echo "ERROR: App did not become reachable inside Docker network within ${max_wait}s." >&2
  docker compose logs app --tail=20 >&2
  exit 1
}

run_k6_in_network() {
  local strategy="$1"
  local scenario="$2"
  local summary_file="/bench/results/${strategy}-${scenario}.json"
  local host_summary="$RESULTS_DIR/${strategy}-${scenario}.json"

  echo "  → Running k6 (in Docker network) for Strategy=${strategy}, Scenario=${scenario}..."

  local k6_exit=0
  docker compose \
    --profile bench \
    run --rm \
    -e SCENARIO="$scenario" \
    -e BASE_URL="$K6_BASE_URL" \
    -e USERS="$USERS" \
    -e VUS="$VUS" \
    -e DURATION="$DURATION" \
    -e WARMUP="$WARMUP" \
    k6 run \
      --summary-export "$summary_file" \
      --summary-trend-stats "avg,med,p(90),p(95),p(99),max" \
      /bench/transfer.js || k6_exit=$?

  if [ $k6_exit -ne 0 ]; then
    echo "ERROR: k6 exited with code $k6_exit for ${strategy} / ${scenario}." >&2
    exit 1
  fi

  # Verify measure-phase request count > 0
  local req_count
  req_count="$(python3 - "$host_summary" <<'EOF'
import json, sys
path = sys.argv[1]
try:
    data = json.load(open(path))
    metrics = data.get("metrics", {})
    tagged = metrics.get("http_reqs{phase:measure}", {})
    c = tagged.get("count", metrics.get("http_reqs", {}).get("count", 0))
    print(int(c))
except Exception:
    print(0)
EOF
)"

  if [ "${req_count:-0}" -le 0 ]; then
    echo "ERROR: Measure phase has 0 requests for ${strategy} / ${scenario}." >&2
    exit 1
  fi
  echo "  → k6 OK: ${req_count} measure-phase requests recorded."
}

run_reconciliation() {
  local strategy="$1"
  local scenario="$2"
  local recon_file="$RESULTS_DIR/${strategy}-${scenario}-recon.json"

  echo "  → Running reconciliation audit (host → localhost:8080)..."
  local recon_res
  if ! recon_res="$(curl -fsS --max-time 90 \
        -X POST "${HOST_BASE_URL}/api/v1/admin/reconciliation/run" \
        -H "X-Admin-Token: ${ADMIN_TOKEN}" \
        -H "Content-Type: application/json")"; then
    echo "ERROR: Reconciliation HTTP request failed (non-2xx) for ${strategy} / ${scenario}." >&2
    exit 1
  fi

  echo "$recon_res" > "$recon_file"

  local recon_status disc_count
  recon_status="$(python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('status',''))" <<< "$recon_res")"
  disc_count="$(python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('discrepancyCount',-1))" <<< "$recon_res")"

  if [ "$recon_status" != "OK" ] || [ "$disc_count" -ne 0 ]; then
    echo "CORRECTNESS FAILURE: recon status='${recon_status}', discrepancyCount=${disc_count} for ${strategy}/${scenario}." >&2
    echo "$recon_res" >&2
    exit 1
  fi
  echo "  → Reconciliation PASSED: status=OK, discrepancies=0."
}

# ── Main ───────────────────────────────────────────────────────────────────

cd "$ROOT_DIR"

if [ "$BENCH_RESET" = "1" ]; then
  echo "=== BENCH_RESET=1: clearing previous results and resetting Docker stack ==="
  rm -f "$RESULTS_DIR"/*.json "$RESULTS_DIR/RESULTS.md"
  docker compose --profile observability --profile bench down -v 2>/dev/null || \
    docker compose down -v 2>/dev/null || true
fi

for strategy in "${STRATEGIES[@]}"; do
  echo ""
  echo "=========================================================="
  echo " Strategy: $strategy"
  echo "=========================================================="

  # Stop the app container (if running), then start it with the new strategy env.
  # Using 'stop + up' rather than '--force-recreate' so the volume and network
  # are preserved across strategy switches (only the env var changes).
  docker compose stop app 2>/dev/null || true
  WALLET_TRANSFER_STRATEGY="$strategy" ADMIN_TOKEN="$ADMIN_TOKEN" \
    docker compose up -d app

  # Health check FROM INSIDE the Docker network — the same path k6 will use.
  wait_for_health_in_network

  for scenario in "${SCENARIOS[@]}"; do
    echo ""
    echo "  ----------------------------------------------------------"
    echo "  Benchmark: Strategy=${strategy}, Scenario=${scenario}"
    echo "  ----------------------------------------------------------"

    run_k6_in_network "$strategy" "$scenario"
    run_reconciliation "$strategy" "$scenario"
  done
done

echo ""
echo "=== Generating summary table ==="
python3 "$SCRIPT_DIR/summarize.py"
echo "=== Benchmark complete! Results written to $RESULTS_DIR/RESULTS.md ==="
