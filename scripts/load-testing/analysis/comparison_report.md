# API Gateway Performance Comparison Report

**Evaluation Objective:** Quantify the performance and reliability trade-offs of API Gateway optimizations (Rate Limiting, Response Caching, and Combined).

---

## LOW Workload (5 Virtual Users, 60s)

| Configuration | Runs | Avg Latency | p95 Latency | p99 Latency | Throughput | Total Reqs | Success | HTTP 429 | Error Rate | Cache Hit Ratio |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Baseline (Unoptimized)** | 0 | *Pending execution* | — | — | — | — | — | — | — | — |
| **Rate Limiting Only** | 0 | *Pending execution* | — | — | — | — | — | — | — | — |
| **Response Caching Only** | 0 | *Pending execution* | — | — | — | — | — | — | — | — |
| **Combined Optimizations** | 0 | *Pending execution* | — | — | — | — | — | — | — | — |


## MEDIUM Workload (20 Virtual Users, 120s)

| Configuration | Runs | Avg Latency | p95 Latency | p99 Latency | Throughput | Total Reqs | Success | HTTP 429 | Error Rate | Cache Hit Ratio |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Baseline (Unoptimized)** | 0 | *Pending execution* | — | — | — | — | — | — | — | — |
| **Rate Limiting Only** | 0 | *Pending execution* | — | — | — | — | — | — | — | — |
| **Response Caching Only** | 0 | *Pending execution* | — | — | — | — | — | — | — | — |
| **Combined Optimizations** | 0 | *Pending execution* | — | — | — | — | — | — | — | — |


## HIGH Workload (50 Virtual Users, 180s)

| Configuration | Runs | Avg Latency | p95 Latency | p99 Latency | Throughput | Total Reqs | Success | HTTP 429 | Error Rate | Cache Hit Ratio |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Baseline (Unoptimized)** | 0 | *Pending execution* | — | — | — | — | — | — | — | — |
| **Rate Limiting Only** | 0 | *Pending execution* | — | — | — | — | — | — | — | — |
| **Response Caching Only** | 0 | *Pending execution* | — | — | — | — | — | — | — | — |
| **Combined Optimizations** | 0 | *Pending execution* | — | — | — | — | — | — | — | — |

