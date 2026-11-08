#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results"

mkdir -p "$RESULTS_DIR"

ADMIN_TOKEN="${ADMIN_TOKEN:-local-admin-token}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
BENCH_RESET="${BENCH_RESET:-0}"
USERS="${USERS:-200}"
VUS="${VUS:-50}"
DURATION="${DURATION:-30s}"
WARMUP="${WARMUP:-10s}"

STRATEGIES=("ORDERED_LOCKS" "ATOMIC_UPDATE" "OPTIMISTIC")
SCENARIOS=("uniform" "hot_wallet" "pingpong")

wait_for_health() {
  local max_wait=120
  local elapsed=0
  echo "Waiting for $BASE_URL/actuator/health to report UP..."
  while [ $elapsed -lt $max_wait ]; do
    if curl -fsS "$BASE_URL/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "Application is UP."
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo "ERROR: Application failed to become UP within ${max_wait}s." >&2
  exit 1
}

run_k6() {
  local strategy="$1"
  local scenario="$2"
  local summary_file="$RESULTS_DIR/${strategy}-${scenario}.json"

  echo "Starting k6 for Strategy=${strategy}, Scenario=${scenario}..."
  if command -v k6 &> /dev/null; then
    SCENARIO="$scenario" \
    BASE_URL="$BASE_URL" \
    USERS="$USERS" \
    VUS="$VUS" \
    DURATION="$DURATION" \
    WARMUP="$WARMUP" \
    k6 run \
      --summary-export "$summary_file" \
      --summary-trend-stats "avg,med,p(90),p(95),p(99),max" \
      "$SCRIPT_DIR/transfer.js" || {
        echo "ERROR: k6 exited with non-zero exit code for ${strategy} / ${scenario}." >&2
        exit 1
      }
  else
    local app_container
    app_container="$(docker compose ps -q app 2>/dev/null || true)"
    if [ -n "$app_container" ]; then
      docker run --rm -i \
        --network="container:${app_container}" \
        -v "$ROOT_DIR:/work" \
        -w /work \
        -e SCENARIO="$scenario" \
        -e BASE_URL="http://localhost:8080" \
        -e USERS="$USERS" \
        -e VUS="$VUS" \
        -e DURATION="$DURATION" \
        -e WARMUP="$WARMUP" \
        grafana/k6 run \
          --summary-export "$summary_file" \
          --summary-trend-stats "avg,med,p(90),p(95),p(99),max" \
          bench/transfer.js || {
            echo "ERROR: dockerized k6 exited with non-zero exit code for ${strategy} / ${scenario}." >&2
            exit 1
          }
    else
      echo "ERROR: Neither local k6 nor running app container found." >&2
      exit 1
    fi
  fi

  # Verify measure phase recorded requests > 0
  local req_count
  req_count="$(python3 -c "
import json, sys
data = json.load(open('$summary_file'))
metrics = data.get('metrics', {})
c = metrics.get('http_reqs{phase:measure}', {}).get('count')
if c is None:
    c = metrics.get('http_reqs', {}).get('count', 0)
print(int(c))
" 2>/dev/null || echo "0")"

  if [ "$req_count" -le 0 ]; then
    echo "ERROR: Measure phase recorded 0 requests for ${strategy} / ${scenario}." >&2
    exit 1
  fi
  echo "k6 run successful: ${req_count} requests recorded in measure phase."
}

run_reconciliation() {
  local strategy="$1"
  local scenario="$2"
  local recon_file="$RESULTS_DIR/${strategy}-${scenario}-recon.json"

  echo "Running post-scenario reconciliation audit..."
  local recon_res
  if ! recon_res="$(curl -fsS -X POST "$BASE_URL/api/v1/admin/reconciliation/run" \
    -H "X-Admin-Token: $ADMIN_TOKEN" \
    -H "Content-Type: application/json")"; then
    echo "ERROR: Reconciliation HTTP request failed (non-200 status code) for ${strategy} / ${scenario}." >&2
    exit 1
  fi

  echo "$recon_res" > "$recon_file"

  local recon_status
  recon_status="$(python3 -c "import json, sys; d = json.load(sys.stdin); print(d.get('status', ''))" <<< "$recon_res")"

  local disc_count
  disc_count="$(python3 -c "import json, sys; d = json.load(sys.stdin); print(d.get('discrepancyCount', -1))" <<< "$recon_res")"

  if [ "$recon_status" != "OK" ] || [ "$disc_count" -ne 0 ]; then
    echo "CORRECTNESS FAILURE: Reconciliation status='${recon_status}', discrepancyCount=${disc_count} for ${strategy} / ${scenario}." >&2
    echo "$recon_res" >&2
    exit 1
  fi
  echo "Reconciliation PASSED: status=OK, discrepancies=0."
}

cd "$ROOT_DIR"

if [ "$BENCH_RESET" = "1" ]; then
  echo "Resetting docker-compose environment (BENCH_RESET=1)..."
  rm -f "$RESULTS_DIR"/*.json "$RESULTS_DIR"/RESULTS.md
  docker compose down -v
fi

for strategy in "${STRATEGIES[@]}"; do
  echo "=========================================================="
  echo "Switching transfer strategy to: $strategy"
  echo "=========================================================="
  WALLET_TRANSFER_STRATEGY="$strategy" ADMIN_TOKEN="$ADMIN_TOKEN" docker compose up -d --force-recreate app
  wait_for_health

  for scenario in "${SCENARIOS[@]}"; do
    echo "----------------------------------------------------------"
    echo "Running benchmark: Strategy=$strategy, Scenario=$scenario"
    echo "----------------------------------------------------------"

    run_k6 "$strategy" "$scenario"
    run_reconciliation "$strategy" "$scenario"
  done
done

echo "Generating summary table..."
python3 "$SCRIPT_DIR/summarize.py"
echo "Benchmark completed successfully! Results written to $RESULTS_DIR/RESULTS.md"
