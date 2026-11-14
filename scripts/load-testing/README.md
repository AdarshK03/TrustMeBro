# API Gateway Load Testing & Optimization Benchmarking Guide

This directory contains the Apache JMeter test plan, workload configuration profiles, automation scripts, results repository, and analytical tooling for **Day 5 Baseline** and **Day 6 Optimization Experiments** of the Cloud-Based API Gateway and Microservices Management Platform.

---

## 1. Directory Structure

```
scripts/
  load-testing/
    api-gateway-baseline.jmx       # Apache JMeter Test Plan XML (v5.6.3+)
    low-workload.properties        # Low workload parameters (5 users, 60s)
    medium-workload.properties     # Medium workload parameters (20 users, 120s)
    high-workload.properties       # High workload parameters (50 users, 180s)
    run-baseline-benchmarks.ps1    # Baseline single-run benchmark script (Day 5)
    run-experiments.ps1            # Multi-run controlled experiment harness (Day 6)
    results/
      baseline/                    # Configuration A raw CSV and JSON summaries
      rate-limit-only/             # Configuration B raw CSV and JSON summaries
      cache-only/                  # Configuration C raw CSV and JSON summaries
      combined/                    # Configuration D raw CSV and JSON summaries
    analysis/
      analyze_results.py           # Automated analysis, aggregation & SVG chart tool
      comparison_summary.json      # Machine-readable comparison matrix for React UI
      comparison_report.md         # Formatted markdown comparison report
      experimental_findings.md     # Detailed analytical findings and trade-offs report
      comparison/                  # Generated comparison SVG charts
    README.md                      # This comprehensive guide
```

---

## 2. Four Experimental Configurations

| Config | Name | Rate Limiting | Response Caching | JVM Configuration Flags |
| :---: | :--- | :---: | :---: | :--- |
| **A** | **Baseline** | Disabled | Disabled | `-DRATE_LIMIT_ENABLED=false -DCACHE_ENABLED=false` |
| **B** | **Rate Limiting Only** | Enabled | Disabled | `-DRATE_LIMIT_ENABLED=true -DRATE_LIMIT_MAX_REQUESTS=100 -DCACHE_ENABLED=false` |
| **C** | **Response Caching Only**| Disabled | Enabled | `-DRATE_LIMIT_ENABLED=false -DCACHE_ENABLED=true -DCACHE_EXPIRATION_SECONDS=60` |
| **D** | **Combined Optimizations**| Enabled | Enabled | `-DRATE_LIMIT_ENABLED=true -DRATE_LIMIT_MAX_REQUESTS=100 -DCACHE_ENABLED=true` |

All four configurations run on the exact same application version (`1.0.0-SNAPSHOT`), using the exact same Spring Boot applications and database dataset.

---

## 3. Workload Specifications

All load tests target the **API Gateway** on port `8080`, not individual downstream microservice ports. The test plan issues a consistent request mix representing real-world client traffic across all three microservices:

1. `GET /api/products` (Product catalog lookup — eligible for Gateway response caching)
2. `GET /api/users` (User identity directory lookup)
3. `GET /api/orders` (Order transaction history lookup)

| Workload Level | Concurrent Virtual Users | Ramp-Up Period | Steady-State Duration | Warm-Up Period | Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Low** | 5 threads | 10 seconds | 60 seconds | 5 seconds | Low-concurrency baseline sanity |
| **Medium** | 20 threads | 20 seconds | 120 seconds | 10 seconds | Normal operational load |
| **High** | 50 threads | 30 seconds | 180 seconds | 15 seconds | High-concurrency stress test |

Every experiment is repeated for at least **3 independent runs** (`run1`, `run2`, `run3`) to evaluate statistical stability and variance.

---

## 4. Starting the Application Stack

### 4.1 Build All Artifacts
From the project root:
```powershell
.\mvnw.cmd clean package -DskipTests
```

### 4.2 Start Downstream Microservices
Open three separate PowerShell terminals (or use `scripts/run-jars.ps1`):

**Terminal 1 — User Service (Port 8081):**
```powershell
java -jar user-service/target/user-service-1.0.0-SNAPSHOT.jar
```

**Terminal 2 — Product Service (Port 8082):**
```powershell
java -jar product-service/target/product-service-1.0.0-SNAPSHOT.jar
```

**Terminal 3 — Order Service (Port 8083):**
```powershell
java -jar order-service/target/order-service-1.0.0-SNAPSHOT.jar
```

### 4.3 Start API Gateway with Target Configuration
In a fourth terminal, start the Gateway selecting the desired configuration:

**For Configuration A (Baseline):**
```powershell
java -DRATE_LIMIT_ENABLED=false -DCACHE_ENABLED=false -DSECURITY_JWT_ENABLED=false -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar
```

**For Configuration B (Rate Limiting Only):**
```powershell
java -DRATE_LIMIT_ENABLED=true -DRATE_LIMIT_MAX_REQUESTS=100 -DRATE_LIMIT_WINDOW_SECONDS=60 -DCACHE_ENABLED=false -DSECURITY_JWT_ENABLED=false -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar
```

**For Configuration C (Response Caching Only):**
```powershell
java -DRATE_LIMIT_ENABLED=false -DCACHE_ENABLED=true -DCACHE_EXPIRATION_SECONDS=60 -DSECURITY_JWT_ENABLED=false -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar
```

**For Configuration D (Combined Optimizations):**
```powershell
java -DRATE_LIMIT_ENABLED=true -DRATE_LIMIT_MAX_REQUESTS=100 -DRATE_LIMIT_WINDOW_SECONDS=60 -DCACHE_ENABLED=true -DCACHE_EXPIRATION_SECONDS=60 -DSECURITY_JWT_ENABLED=false -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar
```

Verify active configuration anytime:
```powershell
curl.exe http://localhost:8080/api/gateway/stats
```

### 4.4 Start the React Frontend
In a fifth terminal:
```powershell
cd frontend
npm run dev
```
Open your browser at `http://localhost:5173`.

---

## 5. Executing Controlled Experiments

The automated script `scripts/load-testing/run-experiments.ps1` manages verification, warm-up, and repeated test execution:

### Run Specific Configuration (e.g. Configuration B):
```powershell
powershell -ExecutionPolicy Bypass -File scripts/load-testing/run-experiments.ps1 `
  -Configuration "rate-limit-only" `
  -WorkloadLevel "all" `
  -Repetitions 3
```

### Run Specific Workload Level (e.g. Medium):
```powershell
powershell -ExecutionPolicy Bypass -File scripts/load-testing/run-experiments.ps1 `
  -Configuration "cache-only" `
  -WorkloadLevel "medium" `
  -Repetitions 3
```

### Output File Naming Convention
Results are saved separately in their respective directories:
- `scripts/load-testing/results/<config>/<config>_<workload>_run1.csv`
- `scripts/load-testing/results/<config>/<config>_<workload>_run2.csv`
- `scripts/load-testing/results/<config>/<config>_<workload>_run3.csv`
- With companion `*_summary.json` files storing application-side telemetry (cache hits, misses, 429 rejections).

---

## 6. Running Performance Analysis & Generating Charts

Execute the Python analysis tool:
```powershell
python scripts/load-testing/analysis/analyze_results.py
```

This processes all raw files across configurations and produces:
1. `scripts/load-testing/analysis/comparison_summary.json` (Structured data for the React dashboard).
2. `scripts/load-testing/analysis/comparison_report.md` (Markdown tables with mean, p95, p99, throughput, and error rates).
3. Standalone vector SVG comparison charts under `scripts/load-testing/analysis/comparison/`:
   - `avg_latency_comparison.svg`
   - `p95_latency_comparison.svg`
   - `p99_latency_comparison.svg`
   - `throughput_comparison.svg`
   - `error_rate_comparison.svg`
   - `cache_hit_ratio_comparison.svg`

---

## 7. Visualizing and Comparing in the React Dashboard

1. Open `http://localhost:5173` and click the **Performance Experiments** tab.
2. Click **Select CSV or JSON File** and upload `scripts/load-testing/analysis/comparison_summary.json` (or any individual run `.csv` / `.json`).
3. The dashboard immediately renders:
   - **Interactive Comparison Chart:** Toggle between Average Latency, p95, Throughput, Error Rate, and Cache Hit %.
   - **Workload Filter Pills:** Filter by Low, Medium, High, or All.
   - **Side-by-Side Comparison Tables:** Complete breakdown across all four configurations.
   - **Active Gateway Optimization Telemetry:** Live stats from `GET /api/gateway/stats`.
