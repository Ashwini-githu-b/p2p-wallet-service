#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results"

mkdir -p "$RESULTS_DIR"

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_TOKEN="${ADMIN_TOKEN:-admin-secret-token}"

STRATEGIES=("ORDERED_LOCKS" "ATOMIC_UPDATE" "OPTIMISTIC")
SCENARIOS=("uniform" "hot_wallet" "pingpong")

set_strategy() {
  local strat="$1"
  echo "=========================================================="
  echo "Switching transfer strategy to: $strat"
  echo "=========================================================="
  curl -s -f -X POST "$BASE_URL/api/v1/admin/strategy" \
    -H "X-Admin-Token: $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"strategy\":\"$strat\"}" > /dev/null || true
  sleep 1
}

run_k6() {
  local strat="$1"
  local scen="$2"
  local out_file="$RESULTS_DIR/${strat}-${scen}.json"
  k6 run \
    --env STRATEGY="$strat" \
    --env SCENARIO="$scen" \
    --env BASE_URL="$BASE_URL" \
    --summary-export="$out_file" \
    "$SCRIPT_DIR/transfer.js"
}

reconcile() {
  local strat="$1"
  local scen="$2"
  echo "Running post-scenario reconciliation..."
  local recon_res
  recon_res="$(curl -s -X POST "$BASE_URL/api/v1/admin/reconciliation/run" \
    -H "X-Admin-Token: $ADMIN_TOKEN" \
    -H "Content-Type: application/json" || echo '{"status":"FAILED"}')"

  echo "$recon_res" > "$RESULTS_DIR/${strat}-${scen}-recon.json"

  local recon_status
  recon_status="$(echo "$recon_res" | grep -o '"status":"[^"]*"' | head -n 1 | cut -d':' -f2 | tr -d '"' || echo "UNKNOWN")"

  if [ "$recon_status" != "OK" ]; then
    echo "CORRECTNESS FAILURE: Reconciliation status is $recon_status for $strat / $scen"
  else
    echo "Reconciliation OK for $strat / $scen"
  fi
}

for strategy in "${STRATEGIES[@]}"; do
  set_strategy "$strategy"
  for scenario in "${SCENARIOS[@]}"; do
    echo "----------------------------------------------------------"
    echo "Running benchmark: Strategy=$strategy, Scenario=$scenario"
    echo "----------------------------------------------------------"
    run_k6 "$strategy" "$scenario"
    reconcile "$strategy" "$scenario"
  done
done

echo "Generating final benchmark summary..."
python3 "$SCRIPT_DIR/summarize.py"
echo "Benchmark run complete! Results written to $RESULTS_DIR/RESULTS.md"
