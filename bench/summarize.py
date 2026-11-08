#!/usr/bin/env python3
import json
import os
import platform
import subprocess
import sys
from datetime import datetime, timezone

RESULTS_DIR = os.path.join(os.path.dirname(__file__), "results")
OUTPUT_FILE = os.path.join(RESULTS_DIR, "RESULTS.md")

STRATEGIES = ["ORDERED_LOCKS", "ATOMIC_UPDATE", "OPTIMISTIC"]
SCENARIOS = ["uniform", "hot_wallet", "pingpong"]

def get_git_commit():
    try:
        return subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], stderr=subprocess.DEVNULL).decode().strip()
    except Exception:
        return "N/A"

def get_cpu_info():
    try:
        if platform.system() == "Darwin":
            return subprocess.check_output(["sysctl", "-n", "machdep.cpu.brand_string"], stderr=subprocess.DEVNULL).decode().strip()
        elif platform.system() == "Linux":
            with open("/proc/cpuinfo") as f:
                for line in f:
                    if "model name" in line:
                        return line.split(":")[1].strip()
    except Exception:
        pass
    return platform.processor() or "Unknown CPU"

def get_ram_info():
    try:
        if platform.system() == "Darwin":
            mem_bytes = int(subprocess.check_output(["sysctl", "-n", "hw.memsize"], stderr=subprocess.DEVNULL).decode().strip())
            return f"{mem_bytes // (1024**3)} GB"
        elif platform.system() == "Linux":
            with open("/proc/meminfo") as f:
                for line in f:
                    if "MemTotal" in line:
                        kb = int(line.split()[1])
                        return f"{kb // (1024**2)} GB"
    except Exception:
        pass
    return "Unknown RAM"

def get_k6_version():
    try:
        out = subprocess.check_output(["k6", "version"], stderr=subprocess.DEVNULL).decode().strip()
        return out
    except Exception:
        pass
    try:
        out = subprocess.check_output(["docker", "run", "--rm", "grafana/k6", "version"], stderr=subprocess.DEVNULL).decode().strip()
        return out
    except Exception:
        return "k6 (Docker)"

def parse_measure_metric(metrics, metric_name, field):
    """
    Extracts the specified field from the metric object in metrics.
    Prioritizes the '{phase:measure}' tagged submetric, then falls back to metric_name.
    Raises ValueError if metric or field is missing (never silently defaults to 0).
    """
    tagged_key = f"{metric_name}{{phase:measure}}"
    obj = metrics.get(tagged_key)
    if obj is None:
        obj = metrics.get(metric_name)
    if obj is None:
        raise ValueError(f"Metric '{tagged_key}' or '{metric_name}' not found in k6 output")

    if "values" in obj and isinstance(obj["values"], dict) and field in obj["values"]:
        return obj["values"][field]
    if field in obj:
        return obj[field]

    raise ValueError(f"Field '{field}' not found in metric '{tagged_key}'. Keys present: {list(obj.keys())}")

def format_latency(val):
    if val is None:
        return "-"
    return f"{float(val):.1f}"

def main():
    os.makedirs(RESULTS_DIR, exist_ok=True)

    date_str = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    git_commit = get_git_commit()
    uname_str = f"{platform.system()} {platform.machine()}"
    cpu_model = get_cpu_info()
    ram_info = get_ram_info()
    k6_ver = get_k6_version()
    vus = os.getenv("VUS", "50")
    duration = os.getenv("DURATION", "30s")
    users = os.getenv("USERS", "200")

    table_data = []

    # Validate and collect data for all strategy x scenario combinations
    for strategy in STRATEGIES:
        for scenario in SCENARIOS:
            json_file = os.path.join(RESULTS_DIR, f"{strategy}-{scenario}.json")
            recon_file = os.path.join(RESULTS_DIR, f"{strategy}-{scenario}-recon.json")

            if not os.path.exists(json_file):
                print(f"Error: Missing k6 benchmark file {json_file}", file=sys.stderr)
                sys.exit(1)
            if not os.path.exists(recon_file):
                print(f"Error: Missing reconciliation file {recon_file}", file=sys.stderr)
                sys.exit(1)

            with open(json_file, "r") as f:
                data = json.load(f)

            metrics = data.get("metrics", {})
            try:
                req_count = int(parse_measure_metric(metrics, "http_reqs", "count"))
                http_reqs_rate = float(parse_measure_metric(metrics, "http_reqs", "rate"))
                p50 = float(parse_measure_metric(metrics, "http_req_duration", "med"))
                p95 = float(parse_measure_metric(metrics, "http_req_duration", "p(95)"))
                p99 = float(parse_measure_metric(metrics, "http_req_duration", "p(99)"))
                c201 = int(parse_measure_metric(metrics, "transfers_201", "count"))
                c422 = int(parse_measure_metric(metrics, "transfers_422", "count"))
                c_other = int(parse_measure_metric(metrics, "transfers_other", "count"))
            except Exception as e:
                print(f"Error parsing metrics for {strategy} {scenario}: {e}", file=sys.stderr)
                sys.exit(1)

            if req_count <= 0:
                print(f"Validation Error: Measure phase has zero requests for {strategy} {scenario} (count={req_count})", file=sys.stderr)
                sys.exit(1)

            with open(recon_file, "r") as rf:
                recon_data = json.load(rf)

            raw_recon_status = recon_data.get("status", "")
            discrepancy_count = recon_data.get("discrepancyCount", -1)

            if raw_recon_status == "OK" and discrepancy_count == 0:
                recon_col = "OK"
            else:
                recon_col = "MISMATCH"

            if recon_col != "OK":
                print(f"Validation Error: Reconciliation failed for {strategy} {scenario} (status={raw_recon_status}, discrepancies={discrepancy_count})", file=sys.stderr)
                sys.exit(1)

            table_data.append({
                "strategy": strategy,
                "scenario": scenario,
                "throughput": http_reqs_rate,
                "p50": p50,
                "p95": p95,
                "p99": p99,
                "c201": c201,
                "c422": c422,
                "c_other": c_other,
                "recon": recon_col
            })

    # Build Markdown
    lines = []
    lines.append("# Wallet Service Benchmark Results")
    lines.append("")
    lines.append(f"- **Date**: {date_str}")
    lines.append(f"- **Git Commit**: `{git_commit}`")
    lines.append(f"- **OS / Arch**: `{uname_str}`")
    lines.append(f"- **CPU**: {cpu_model}")
    lines.append(f"- **RAM**: {ram_info}")
    lines.append(f"- **Docker Limits**: Default host limits")
    lines.append(f"- **k6 Version**: `{k6_ver}`")
    lines.append(f"- **Parameters**: {vus} VUs, {duration} measure duration, {users} initial funded users")
    lines.append("")
    lines.append("> **Note**: Results are measured on a single local machine with PostgreSQL running in Docker.")
    lines.append("")
    lines.append("| Strategy | Scenario | Throughput (req/s) | p50 ms | p95 ms | p99 ms | 201 | 422 | other | Reconciliation |")
    lines.append("|---|---|---|---|---|---|---|---|---|---|")

    for row in table_data:
        s_thru = f"{row['throughput']:.1f}"
        s_p50 = format_latency(row["p50"])
        s_p95 = format_latency(row["p95"])
        s_p99 = format_latency(row["p99"])
        line = f"| {row['strategy']} | {row['scenario']} | {s_thru} | {s_p50} | {s_p95} | {s_p99} | {row['c201']} | {row['c422']} | {row['c_other']} | {row['recon']} |"
        lines.append(line)

    lines.append("")
    lines.append("### Observations")
    lines.append("")

    # Generate observations programmatically and factually from numbers only
    obs_lines = []

    # 1. Top throughput per scenario
    for scen in SCENARIOS:
        scen_rows = [r for r in table_data if r["scenario"] == scen]
        best_row = max(scen_rows, key=lambda r: r["throughput"])
        obs_lines.append(
            f"- In the `{scen}` scenario, `{best_row['strategy']}` achieved the highest throughput at {best_row['throughput']:.1f} req/s (p50: {best_row['p50']:.1f} ms, p95: {best_row['p95']:.1f} ms)."
        )

    # 2. Other / conflict responses per strategy
    for strat in STRATEGIES:
        strat_rows = [r for r in table_data if r["strategy"] == strat]
        total_201 = sum(r["c201"] for r in strat_rows)
        total_422 = sum(r["c422"] for r in strat_rows)
        total_other = sum(r["c_other"] for r in strat_rows)
        if total_other > 0:
            scen_breakdown = ", ".join(f"{r['scenario']}: {r['c_other']}" for r in strat_rows if r["c_other"] > 0)
            obs_lines.append(
                f"- `{strat}` recorded {total_other} non-201/non-422 ('other') responses ({scen_breakdown}) due to retry exhaustion under contention, alongside {total_201} successful 201 transfers."
            )
        else:
            obs_lines.append(
                f"- `{strat}` recorded 0 'other' responses across all scenarios with {total_201} successful 201 transfers."
            )

    # 3. Reconciliation verification
    all_ok = all(r["recon"] == "OK" for r in table_data)
    if all_ok:
        obs_lines.append(
            f"- Post-scenario reconciliation passed with status `OK` (0 discrepancies) across all {len(table_data)} benchmark runs."
        )

    lines.extend(obs_lines)

    # Write output
    with open(OUTPUT_FILE, "w") as f:
        f.write("\n".join(lines) + "\n")

    print(f"Summary written to {OUTPUT_FILE}")

if __name__ == "__main__":
    main()
