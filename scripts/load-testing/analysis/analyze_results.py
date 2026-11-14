#!/usr/bin/env python3
"""
Performance Results Analysis Tool
Cloud-Based API Gateway and Microservices Management Platform (Day 6)

Analyzes raw experiment results (CSV and JSON) across four gateway configurations:
  1. Baseline (A)
  2. Rate Limiting Only (B)
  3. Response Caching Only (C)
  4. Combined (D)

Calculates:
  - Average, p95, p99 latencies
  - Throughput (req/s)
  - Error rates & HTTP 429 rate limit rejections (distinguished from backend failures)
  - Cache hit counts, miss counts, and hit ratios
  - Aggregates repeated runs (Run 1, Run 2, Run 3)
Generates:
  - Markdown comparison report
  - Structured comparison_summary.json (for React UI)
  - Standalone SVG comparison charts
"""

import os
import sys
import csv
import json
import math
import glob
from pathlib import Path
from collections import defaultdict

CONFIG_LABELS = {
    "baseline": "Baseline (Unoptimized)",
    "rate-limit-only": "Rate Limiting Only",
    "cache-only": "Response Caching Only",
    "combined": "Combined Optimizations"
}

CONFIG_COLORS = {
    "baseline": "#94a3b8",      # Slate gray
    "rate-limit-only": "#f59e0b", # Amber
    "cache-only": "#3b82f6",     # Blue
    "combined": "#10b981"       # Emerald green
}

WORKLOAD_ORDER = ["low", "medium", "high"]


def parse_csv_run(file_path):
    """Parses a JMeter or benchmark harness CSV output file."""
    samples = []
    with open(file_path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        if not header:
            return None

        # Clean header strings
        header = [h.strip().replace('"', '') for h in header]
        
        # Locate required columns
        try:
            elapsed_idx = header.index("elapsed") if "elapsed" in header else header.index("Latency")
            code_idx = header.index("responseCode") if "responseCode" in header else header.index("code")
            success_idx = header.index("success") if "success" in header else -1
            label_idx = header.index("label") if "label" in header else -1
            time_idx = header.index("timeStamp") if "timeStamp" in header else -1
        except ValueError as e:
            print(f"[WARN] Skipping {file_path}: Missing essential column ({e})")
            return None

        for row in reader:
            if not row or len(row) <= max(elapsed_idx, code_idx):
                continue
            try:
                elapsed = float(row[elapsed_idx])
                code = str(row[code_idx]).strip()
                success = (row[success_idx].strip().lower() == "true") if success_idx != -1 else (code.startswith("2") or code.startswith("3"))
                label = row[label_idx].strip() if label_idx != -1 and len(row) > label_idx else "Request"
                ts = int(row[time_idx]) if time_idx != -1 and len(row) > time_idx and row[time_idx].isdigit() else None

                samples.append({
                    "elapsed": elapsed,
                    "code": code,
                    "success": success,
                    "label": label,
                    "timeStamp": ts
                })
            except (ValueError, IndexError):
                continue

    if not samples:
        return None

    total_requests = len(samples)
    successful_requests = sum(1 for s in samples if s["success"])
    failed_requests = total_requests - successful_requests

    # Distinguish HTTP 429 rate limit rejections from 5xx backend errors
    rejections_429 = sum(1 for s in samples if s["code"] == "429")
    backend_errors_5xx = sum(1 for s in samples if s["code"].startswith("5"))
    client_errors_4xx = sum(1 for s in samples if s["code"].startswith("4") and s["code"] != "429")

    # Latency percentiles
    all_elapsed = sorted(s["elapsed"] for s in samples)
    avg_latency = sum(all_elapsed) / total_requests
    p95_idx = min(int(total_requests * 0.95), total_requests - 1)
    p95_latency = all_elapsed[p95_idx]

    # Only report p99 if sample size >= 100
    p99_latency = None
    if total_requests >= 100:
        p99_idx = min(int(total_requests * 0.99), total_requests - 1)
        p99_latency = all_elapsed[p99_idx]

    # Throughput calculation
    valid_ts = [s["timeStamp"] for s in samples if s["timeStamp"] is not None]
    throughput = 0.0
    duration_sec = 0.0
    if len(valid_ts) >= 2:
        duration_sec = (max(valid_ts) - min(valid_ts)) / 1000.0
        if duration_sec > 0:
            throughput = total_requests / duration_sec

    # Error rate
    error_rate = (failed_requests / total_requests) * 100.0 if total_requests > 0 else 0.0

    return {
        "file": os.path.basename(file_path),
        "total_requests": total_requests,
        "successful_requests": successful_requests,
        "failed_requests": failed_requests,
        "rejections_429": rejections_429,
        "backend_errors_5xx": backend_errors_5xx,
        "client_errors_4xx": client_errors_4xx,
        "error_rate": round(error_rate, 2),
        "avg_latency": round(avg_latency, 2),
        "p95_latency": round(p95_latency, 2),
        "p99_latency": round(p99_latency, 2) if p99_latency is not None else None,
        "throughput": round(throughput, 2),
        "duration_sec": round(duration_sec, 1)
    }


def parse_summary_json(file_path):
    """Parses a companion summary JSON file for telemetry such as cache stats."""
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            data = json.load(f)
            return data
    except Exception:
        return None


def collect_experiments(results_dir):
    """Scans and parses all benchmark runs across all configurations."""
    results_path = Path(results_dir)
    data = defaultdict(lambda: defaultdict(list))

    configs = ["baseline", "rate-limit-only", "cache-only", "combined"]
    workloads = ["low", "medium", "high"]

    for config in configs:
        config_dir = results_path / config
        if not config_dir.exists():
            continue

        for workload in workloads:
            # Check pattern 1: results/<config>/<workload>/*.csv
            workload_sub = config_dir / workload
            candidate_files = []
            if workload_sub.exists():
                candidate_files.extend(workload_sub.glob("*.csv"))

            # Check pattern 2: results/<config>/<config>_<workload>_run*.csv
            candidate_files.extend(config_dir.glob(f"*{workload}*.csv"))

            seen_paths = set()
            for csv_file in candidate_files:
                resolved = str(csv_file.resolve())
                if resolved in seen_paths:
                    continue
                seen_paths.add(resolved)

                run_metrics = parse_csv_run(str(csv_file))
                if run_metrics:
                    # Look for matching summary.json for cache hits/misses
                    json_file = csv_file.with_suffix(".json")
                    summary_json = None
                    if json_file.exists():
                        summary_json = parse_summary_json(str(json_file))
                    else:
                        gen_summary = csv_file.parent / "summary.json"
                        if gen_summary.exists():
                            summary_json = parse_summary_json(str(gen_summary))

                    if summary_json:
                        run_metrics["cache_hits"] = summary_json.get("cacheHits", summary_json.get("caching", {}).get("hitCount", 0))
                        run_metrics["cache_misses"] = summary_json.get("cacheMisses", summary_json.get("caching", {}).get("missCount", 0))
                        run_metrics["cache_hit_ratio"] = summary_json.get("cacheHitRatio", summary_json.get("caching", {}).get("hitRatioPercent", 0.0))
                    else:
                        run_metrics["cache_hits"] = 0
                        run_metrics["cache_misses"] = 0
                        run_metrics["cache_hit_ratio"] = 0.0

                    data[config][workload].append(run_metrics)

    return data


def aggregate_runs(runs_list):
    """Calculates mean and standard deviation across repeated runs."""
    if not runs_list:
        return None

    n = len(runs_list)
    avg_latencies = [r["avg_latency"] for r in runs_list]
    p95_latencies = [r["p95_latency"] for r in runs_list]
    p99_latencies = [r["p99_latency"] for r in runs_list if r["p99_latency"] is not None]
    throughputs = [r["throughput"] for r in runs_list]
    total_reqs = [r["total_requests"] for r in runs_list]
    success_reqs = [r["successful_requests"] for r in runs_list]
    failed_reqs = [r["failed_requests"] for r in runs_list]
    error_rates = [r["error_rate"] for r in runs_list]
    rejections_429 = [r["rejections_429"] for r in runs_list]
    cache_hits = [r.get("cache_hits", 0) for r in runs_list]
    cache_misses = [r.get("cache_misses", 0) for r in runs_list]
    cache_hit_ratios = [r.get("cache_hit_ratio", 0.0) for r in runs_list]

    mean_avg_lat = sum(avg_latencies) / n
    mean_p95 = sum(p95_latencies) / n
    mean_p99 = (sum(p99_latencies) / len(p99_latencies)) if p99_latencies else None
    mean_tp = sum(throughputs) / n
    mean_tot = sum(total_reqs) / n
    mean_succ = sum(success_reqs) / n
    mean_fail = sum(failed_reqs) / n
    mean_err = sum(error_rates) / n
    mean_429 = sum(rejections_429) / n
    mean_hits = sum(cache_hits) / n
    mean_misses = sum(cache_misses) / n
    mean_hit_ratio = sum(cache_hit_ratios) / n

    std_avg_lat = math.sqrt(sum((x - mean_avg_lat) ** 2 for x in avg_latencies) / n) if n > 1 else 0.0

    return {
        "runs_count": n,
        "avg_latency": round(mean_avg_lat, 2),
        "std_avg_latency": round(std_avg_lat, 2),
        "p95_latency": round(mean_p95, 2),
        "p99_latency": round(mean_p99, 2) if mean_p99 is not None else None,
        "throughput": round(mean_tp, 2),
        "total_requests": int(round(mean_tot)),
        "successful_requests": int(round(mean_succ)),
        "failed_requests": int(round(mean_fail)),
        "rejections_429": int(round(mean_429)),
        "error_rate": round(mean_err, 2),
        "cache_hits": int(round(mean_hits)),
        "cache_misses": int(round(mean_misses)),
        "cache_hit_ratio": round(mean_hit_ratio, 2),
        "runs": runs_list
    }


def generate_svg_bar_chart(title, y_label, series_data, output_path, y_format="{:.1f}", is_percentage=False):
    """
    Generates a high-quality SVG bar chart comparing configurations across low/medium/high workloads.
    series_data: { workload: { config: value } }
    """
    width = 720
    height = 420
    margin_top = 60
    margin_right = 160
    margin_bottom = 60
    margin_left = 70

    chart_w = width - margin_left - margin_right
    chart_h = height - margin_top - margin_bottom

    workloads = [w for w in WORKLOAD_ORDER if w in series_data]
    if not workloads:
        return

    configs = ["baseline", "rate-limit-only", "cache-only", "combined"]
    
    # Determine max Y value
    all_values = []
    for w in workloads:
        for c in configs:
            v = series_data.get(w, {}).get(c)
            if v is not None and not math.isnan(v):
                all_values.append(v)

    max_val = max(all_values) if all_values else 100
    if is_percentage:
        max_val = max(100.0, max_val)
    else:
        max_val = max(max_val * 1.15, 1.0)

    # Round max_val up to nice interval
    pow10 = 10 ** math.floor(math.log10(max_val or 1))
    norm = max_val / pow10
    if norm <= 2:
        step = 0.5 * pow10
    elif norm <= 5:
        step = 1.0 * pow10
    else:
        step = 2.0 * pow10
    y_max = math.ceil(max_val / step) * step

    svg = []
    svg.append(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="100%" height="100%" style="background:#0f172a; font-family:-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif;">')

    # Title
    svg.append(f'  <text x="{width/2}" y="32" text-anchor="middle" fill="#f8fafc" font-size="16" font-weight="600">{title}</text>')

    # Background grid lines
    num_grid = 5
    for i in range(num_grid + 1):
        y_val = (y_max / num_grid) * i
        y_pos = margin_top + chart_h - (y_val / y_max) * chart_h
        svg.append(f'  <line x1="{margin_left}" y1="{y_pos}" x2="{margin_left + chart_w}" y2="{y_pos}" stroke="#334155" stroke-dasharray="3,3" stroke-width="1"/>')
        val_str = y_format.format(y_val)
        if is_percentage:
            val_str += "%"
        svg.append(f'  <text x="{margin_left - 10}" y="{y_pos + 4}" text-anchor="end" fill="#94a3b8" font-size="11">{val_str}</text>')

    # Y-axis label
    svg.append(f'  <text transform="rotate(-90)" x="-{(margin_top + chart_h/2)}" y="20" text-anchor="middle" fill="#cbd5e1" font-size="12">{y_label}</text>')

    # Bars
    group_width = chart_w / len(workloads)
    bar_width = (group_width * 0.7) / len(configs)
    
    for w_idx, w in enumerate(workloads):
        group_center = margin_left + (w_idx + 0.5) * group_width
        group_start = group_center - (len(configs) * bar_width) / 2

        # Workload X-axis label
        wl_title = f"{w.capitalize()} Load"
        svg.append(f'  <text x="{group_center}" y="{margin_top + chart_h + 24}" text-anchor="middle" fill="#e2e8f0" font-size="12" font-weight="600">{wl_title}</text>')

        for c_idx, c in enumerate(configs):
            val = series_data.get(w, {}).get(c)
            bar_x = group_start + c_idx * bar_width
            color = CONFIG_COLORS.get(c, "#64748b")

            if val is not None and val > 0:
                bar_h = (val / y_max) * chart_h
                bar_y = margin_top + chart_h - bar_h
                svg.append(f'  <rect x="{bar_x:.1f}" y="{bar_y:.1f}" width="{bar_width*0.85:.1f}" height="{bar_h:.1f}" fill="{color}" rx="2"/>')
                # Value label over bar
                val_txt = f"{val:.1f}" if val < 100 else f"{int(val)}"
                if is_percentage:
                    val_txt += "%"
                svg.append(f'  <text x="{bar_x + (bar_width*0.85)/2:.1f}" y="{bar_y - 4:.1f}" text-anchor="middle" fill="{color}" font-size="9" font-weight="500">{val_txt}</text>')
            else:
                # Value is 0 or N/A
                zero_y = margin_top + chart_h
                svg.append(f'  <rect x="{bar_x:.1f}" y="{zero_y-2:.1f}" width="{bar_width*0.85:.1f}" height="2" fill="{color}" opacity="0.4"/>')
                svg.append(f'  <text x="{bar_x + (bar_width*0.85)/2:.1f}" y="{zero_y - 4:.1f}" text-anchor="middle" fill="#64748b" font-size="8">0</text>')

    # Legend
    leg_x = margin_left + chart_w + 18
    leg_y = margin_top + 10
    svg.append(f'  <text x="{leg_x}" y="{leg_y}" fill="#f8fafc" font-size="11" font-weight="600">Configurations</text>')
    for idx, c in enumerate(configs):
        y_item = leg_y + 20 + idx * 24
        color = CONFIG_COLORS.get(c, "#64748b")
        label = CONFIG_LABELS.get(c, c)
        svg.append(f'  <rect x="{leg_x}" y="{y_item}" width="12" height="12" fill="{color}" rx="2"/>')
        svg.append(f'  <text x="{leg_x + 18}" y="{y_item + 10}" fill="#cbd5e1" font-size="11">{label}</text>')

    svg.append('</svg>')

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        f.write("\n".join(svg))
    print(f"✓ Generated SVG chart: {output_path}")


def main():
    script_dir = Path(__file__).resolve().parent
    base_dir = script_dir.parent
    results_dir = base_dir / "results"
    output_dir = script_dir / "comparison"

    print("==================================================================")
    print(" Performance Results Analysis Tool (Day 6)")
    print(f" Target Directory: {results_dir}")
    print("==================================================================")

    data = collect_experiments(results_dir)

    # Build summarized aggregation
    summary = {}
    has_measurements = False

    for config in ["baseline", "rate-limit-only", "cache-only", "combined"]:
        summary[config] = {}
        for wl in WORKLOAD_ORDER:
            runs = data[config][wl]
            agg = aggregate_runs(runs)
            if agg:
                summary[config][wl] = agg
                has_measurements = True
            else:
                summary[config][wl] = None

    # Write comparison_summary.json
    json_path = script_dir / "comparison_summary.json"
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)
    print(f"✓ Written structured summary: {json_path}")

    # Generate Markdown Comparison Report
    md_lines = []
    md_lines.append("# API Gateway Performance Comparison Report")
    md_lines.append("\n**Evaluation Objective:** Quantify the performance and reliability trade-offs of API Gateway optimizations (Rate Limiting, Response Caching, and Combined).")
    md_lines.append("\n---\n")

    for wl in WORKLOAD_ORDER:
        wl_title = f"{wl.upper()} Workload"
        thread_info = "5 Virtual Users, 60s" if wl == "low" else "20 Virtual Users, 120s" if wl == "medium" else "50 Virtual Users, 180s"
        md_lines.append(f"## {wl_title} ({thread_info})\n")
        md_lines.append("| Configuration | Runs | Avg Latency | p95 Latency | p99 Latency | Throughput | Total Reqs | Success | HTTP 429 | Error Rate | Cache Hit Ratio |")
        md_lines.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")

        for cfg in ["baseline", "rate-limit-only", "cache-only", "combined"]:
            cfg_label = CONFIG_LABELS[cfg]
            m = summary[cfg][wl]
            if m:
                p99_str = f"{m['p99_latency']} ms" if m['p99_latency'] is not None else "N/A"
                hit_ratio_str = f"{m['cache_hit_ratio']}%" if cfg in ["cache-only", "combined"] else "N/A"
                md_lines.append(f"| **{cfg_label}** | {m['runs_count']} | {m['avg_latency']} ms | {m['p95_latency']} ms | {p99_str} | {m['throughput']} req/s | {m['total_requests']} | {m['successful_requests']} | {m['rejections_429']} | {m['error_rate']}% | {hit_ratio_str} |")
            else:
                md_lines.append(f"| **{cfg_label}** | 0 | *Pending execution* | — | — | — | — | — | — | — | — |")
        md_lines.append("\n")

    report_path = script_dir / "comparison_report.md"
    with open(report_path, "w", encoding="utf-8") as f:
        f.write("\n".join(md_lines))
    print(f"✓ Written markdown report: {report_path}")

    # Generate Comparison Charts if any data is present
    if has_measurements:
        # 1. Average Response Time
        avg_series = {wl: {cfg: summary[cfg][wl]["avg_latency"] for cfg in summary if summary[cfg][wl]} for wl in WORKLOAD_ORDER}
        generate_svg_bar_chart("Average Response Time Across Configurations", "Latency (ms)", avg_series, output_dir / "avg_latency_comparison.svg")

        # 2. p95 Latency
        p95_series = {wl: {cfg: summary[cfg][wl]["p95_latency"] for cfg in summary if summary[cfg][wl]} for wl in WORKLOAD_ORDER}
        generate_svg_bar_chart("p95 Latency Across Configurations", "p95 Latency (ms)", p95_series, output_dir / "p95_latency_comparison.svg")

        # 3. p99 Latency
        p99_series = {wl: {cfg: summary[cfg][wl]["p99_latency"] for cfg in summary if summary[cfg][wl] and summary[cfg][wl]["p99_latency"]} for wl in WORKLOAD_ORDER}
        generate_svg_bar_chart("p99 Tail Latency Across Configurations", "p99 Latency (ms)", p99_series, output_dir / "p99_latency_comparison.svg")

        # 4. Throughput
        tp_series = {wl: {cfg: summary[cfg][wl]["throughput"] for cfg in summary if summary[cfg][wl]} for wl in WORKLOAD_ORDER}
        generate_svg_bar_chart("Throughput Comparison Across Configurations", "Requests / Second", tp_series, output_dir / "throughput_comparison.svg")

        # 5. HTTP Error Rate
        err_series = {wl: {cfg: summary[cfg][wl]["error_rate"] for cfg in summary if summary[cfg][wl]} for wl in WORKLOAD_ORDER}
        generate_svg_bar_chart("HTTP Error Rate Across Configurations", "Error Rate (%)", err_series, output_dir / "error_rate_comparison.svg", is_percentage=True)

        # 6. Cache Hit Ratio
        cache_series = {wl: {cfg: summary[cfg][wl]["cache_hit_ratio"] for cfg in ["cache-only", "combined"] if summary[cfg][wl]} for wl in WORKLOAD_ORDER}
        generate_svg_bar_chart("Cache Hit Ratio Across Caching Configurations", "Hit Ratio (%)", cache_series, output_dir / "cache_hit_ratio_comparison.svg", is_percentage=True)
    else:
        print("[INFO] No experiment output files found yet. Test plans and analysis pipelines are initialized.")

    print("\nAnalysis processing complete.")


if __name__ == "__main__":
    main()

