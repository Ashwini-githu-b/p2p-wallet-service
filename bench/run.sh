#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results"

mkdir -p "$RESULTS_DIR"

ADMIN_TOKEN="${ADMIN_TOKEN:-local-admin-token}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
BENCH_RESET="${BENCH_RESET:-0}"

STRATEGIES=("ORDERED_LOCKS" "ATOMIC_UPDATE" "OPTIMISTIC")
SCENARIOS=("uniform" "hot_wallet" "pingpong")

# Check if k6 is installed natively or available via Docker
RUN_K6=""
if command -v k6 &> /dev/null; then
  RUN_K6="k6"
elif command -v docker &> /dev/null; then
  RUN_K6="docker-k6"
else
  echo "Error: neither k6 nor docker is available to run benchmarks."
  exit 1
fi

wait_for_health() {
  local max_wait=60
  local elapsed=0
  echo "Waiting for $BASE_URL/actuator/health to report UP..."
  while [ $elapsed -lt $max_wait ]; do
    if curl -s -f "$BASE_URL/actuator/health" | grep -q '"status":"UP"'; then
      echo "Application is UP."
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo "Application failed to become UP after ${max_wait}s."
  exit 1
}

run_k6() {
  local strategy="$1"
  local scenario="$2"
  local summary_file="bench/results/${strategy}-${scenario}.json"

  if [ "$RUN_K6" = "k6" ]; then
    SCENARIO="$scenario" BASE_URL="$BASE_URL" k6 run \
      --summary-export "$summary_file" \
      --summary-trend-stats "avg,med,p(90),p(95),p(99),max" \
      bench/transfer.js
  else
    # Run via docker container sharing network with app container or host
    local app_container
    app_container="$(docker compose ps -q app 2>/dev/null || true)"
    if [ -n "$app_container" ]; then
      docker run --rm -i \
        --network="container:${app_container}" \
        -v "$ROOT_DIR:/work" \
        -w /work \
        -e SCENARIO="$scenario" \
        -e BASE_URL="http://localhost:8080" \
        -e USERS="${USERS:-200}" \
        -e VUS="${VUS:-50}" \
        -e DURATION="${DURATION:-30s}" \
        -e WARMUP="${WARMUP:-10s}" \
        grafana/k6 run \
          --summary-export "$summary_file" \
          --summary-trend-stats "avg,med,p(90),p(95),p(99),max" \
          bench/transfer.js
    else
      docker run --rm -i \
        --network host \
        -v "$ROOT_DIR:/work" \
        -w /work \
        -e SCENARIO="$scenario" \
        -e BASE_URL="$BASE_URL" \
        -e USERS="${USERS:-200}" \
        -e VUS="${VUS:-50}" \
        -e DURATION="${DURATION:-30s}" \
        -e WARMUP="${WARMUP:-10s}" \
        grafana/k6 run \
          --summary-export "$summary_file" \
          --summary-trend-stats "avg,med,p(90),p(95),p(99),max" \
          bench/transfer.js
    fi
  fi
}

cd "$ROOT_DIR"

if [ "$BENCH_RESET" = "1" ]; then
  echo "Resetting docker compose environment..."
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

    echo "Running post-scenario reconciliation..."
    recon_res="$(curl -s -X POST "$BASE_URL/api/v1/admin/reconciliation/run" \
      -H "X-Admin-Token: $ADMIN_TOKEN" \
      -H "Content-Type: application/json")"

    echo "$recon_res" > "$RESULTS_DIR/${strategy}-${scenario}-recon.json"

    recon_status
    recon_status="$(echo "$recon_res" | grep -o '"status":"[^"]*"' | cut -d':' -f2 | tr -d '"' || true)"

    if [ "$recon_status" != "OK" ]; then
      echo "CORRECTNESS FAILURE: Reconciliation status is $recon_status for $strategy / $scenario"
      echo "$recon_res"
      exit 1
    fi
    echo "Reconciliation PASSED (status OK)."
  done
done

echo "Generating summary table..."
python3 "$SCRIPT_DIR/summarize.py"
echo "Benchmark run completed successfully."

