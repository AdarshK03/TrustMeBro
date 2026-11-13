# API Gateway Load Testing & Baseline Benchmarking Guide

This directory contains the Apache JMeter test plan, workload configuration profiles, automation scripts, and results repository for **Day 5 Baseline Benchmarking** of the Cloud-Based API Gateway and Microservices Management Platform.

---

## 1. Directory Structure

```
scripts/
  load-testing/
    api-gateway-baseline.jmx       # Apache JMeter Test Plan XML (v5.6.3+)
    low-workload.properties        # Low workload parameters (5 users, 60s)
    medium-workload.properties     # Medium workload parameters (20 users, 120s)
    high-workload.properties       # High workload parameters (50 users, 180s)
    run-baseline-benchmarks.ps1    # Automated benchmark execution script
    results/
      baseline/
        low/                       # Low workload raw CSV and summary JSON
        medium/                    # Medium workload raw CSV and summary JSON
        high/                      # High workload raw CSV and summary JSON
    README.md                      # This comprehensive guide
```

---

## 2. Workload Specifications

All load tests target the **API Gateway** on port `8080`, not individual downstream microservice ports. The test plan issues a consistent request mix representing real-world client traffic across all three microservices:

1. `GET /api/products` (Product catalog lookup)
2. `GET /api/users` (User identity directory lookup)
3. `GET /api/orders` (Order transaction history lookup)

| Workload Level | Concurrent Virtual Users | Ramp-Up Period | Steady-State Duration | Warm-Up Period | Target Throughput / Behavior |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Low** | 5 threads | 10 seconds | 60 seconds | 5 seconds | Low concurrency baseline sanity |
| **Medium** | 20 threads | 20 seconds | 120 seconds | 10 seconds | Normal operational load |
| **High** | 50 threads | 30 seconds | 180 seconds | 15 seconds | High concurrency stress baseline |

### Baseline Configuration Rules
- **Gateway Routing:** Enabled (Spring Cloud Gateway WebFlux on port 8080).
- **Rate Limiting:** Disabled (`RATE_LIMIT_ENABLED=false`).
- **Response Caching:** Disabled (`CACHE_ENABLED=false`).
- **Application Version:** Unified snapshot build (`1.0.0-SNAPSHOT`).
- **Dataset:** Consistent seed dataset across all runs.

---

## 3. Prerequisites & Installation

### 3.1 Java Runtime
Ensure JDK 17+ or JDK 21+ is available on your machine:
```powershell
java -version
```

### 3.2 Apache JMeter Installation

#### Option A: Install via Windows Package Manager (`winget`)
```powershell
winget install Apache.JMeter
```

#### Option B: Install via Chocolatey
```powershell
choco install jmeter
```

#### Option C: Manual Zip Download
1. Download Apache JMeter 5.6.3 binary archive from:  
   https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.zip
2. Extract to a local directory (e.g., `C:\tools\apache-jmeter-5.6.3`).
3. Add `C:\tools\apache-jmeter-5.6.3\bin` to your system `PATH`, or define `JMETER_HOME`:
   ```powershell
   $env:JMETER_HOME = "C:\tools\apache-jmeter-5.6.3"
   $env:PATH += ";$env:JMETER_HOME\bin"
   ```

Verify JMeter CLI:
```powershell
jmeter -v
```

---

## 4. Starting the Application Stack

Before executing load tests, start the four backend Spring Boot applications and the React frontend.

### 4.1 Build All Artifacts
From the project root:
```powershell
.\mvnw.cmd clean package -DskipTests
```

### 4.2 Start the Backend Services
Open four separate PowerShell terminals (or use `scripts/run-jars.ps1`):

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

**Terminal 4 — API Gateway (Port 8080 Baseline Configuration):**
```powershell
java -DSECURITY_JWT_ENABLED=false -DRATE_LIMIT_ENABLED=false -DCACHE_ENABLED=false -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar
```

> **Note:** For baseline tests without authentication overhead, run the Gateway with `-DSECURITY_JWT_ENABLED=false`. If running with security enabled (`-DSECURITY_JWT_ENABLED=true`), pass an admin JWT token using `-AuthToken <token>` or `-Jauth.token=<token>`.

### 4.3 Verify Service Health
Run a quick health check to verify all services are active:
```powershell
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8080/api/health/users
curl.exe http://localhost:8080/api/health/products
curl.exe http://localhost:8080/api/health/orders
```
All should return `{"status":"UP"}`.

### 4.4 Start the React Frontend (Port 5173 / 3000)
In a fifth terminal:
```powershell
cd frontend
npm install
npm run dev
```
Open your browser at `http://localhost:5173`.

---

## 5. Executing the Load Tests

### 5.1 Using the Automated Runner Script
The automated PowerShell script `run-baseline-benchmarks.ps1` validates gateway health, verifies endpoints, and executes the specified workloads:

#### Run All Three Workloads Sequentially:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/load-testing/run-baseline-benchmarks.ps1 -WorkloadLevel all
```

#### Run Individual Workloads:
```powershell
# Low Workload (5 users, 60s)
powershell -ExecutionPolicy Bypass -File scripts/load-testing/run-baseline-benchmarks.ps1 -WorkloadLevel low

# Medium Workload (20 users, 120s)
powershell -ExecutionPolicy Bypass -File scripts/load-testing/run-baseline-benchmarks.ps1 -WorkloadLevel medium

# High Workload (50 users, 180s)
powershell -ExecutionPolicy Bypass -File scripts/load-testing/run-baseline-benchmarks.ps1 -WorkloadLevel high
```

#### Custom Gateway URL or Token:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/load-testing/run-baseline-benchmarks.ps1 `
  -GatewayUrl "http://localhost:8080" `
  -AuthToken "eyJhbGciOi..."
```

### 5.2 Executing JMeter Manually via Command Line (Non-GUI Mode)
To run JMeter directly from the terminal without the wrapper script:

```powershell
# Low Workload
jmeter -n -t scripts/load-testing/api-gateway-baseline.jmx `
  -q scripts/load-testing/low-workload.properties `
  -l scripts/load-testing/results/baseline/low/results.csv `
  -e -o scripts/load-testing/results/baseline/low/dashboard

# Medium Workload
jmeter -n -t scripts/load-testing/api-gateway-baseline.jmx `
  -q scripts/load-testing/medium-workload.properties `
  -l scripts/load-testing/results/baseline/medium/results.csv `
  -e -o scripts/load-testing/results/baseline/medium/dashboard

# High Workload
jmeter -n -t scripts/load-testing/api-gateway-baseline.jmx `
  -q scripts/load-testing/high-workload.properties `
  -l scripts/load-testing/results/baseline/high/results.csv `
  -e -o scripts/load-testing/results/baseline/high/dashboard
```

---

## 6. Exporting and Visualizing Results in the React Interface

Once benchmark runs have completed:

1. Open the React frontend at `http://localhost:5173`.
2. Navigate to the **Performance Experiments** tab.
3. Click **Select CSV or JSON File**.
4. Browse to:
   - `scripts/load-testing/results/baseline/low/results.csv` (or `summary.json`)
   - `scripts/load-testing/results/baseline/medium/results.csv` (or `summary.json`)
   - `scripts/load-testing/results/baseline/high/results.csv` (or `summary.json`)
5. The interface immediately computes and visualizes:
   - **Average Response Time** (ms)
   - **p95 Latency** (ms)
   - **p99 Latency** (ms)
   - **Throughput** (requests per second)
   - **Total Requests**
   - **Successful Requests**
   - **Failed Requests**
   - **HTTP Error Rate** (%)
   - **Per-Endpoint Breakdown Table**
   - **Raw Sample Inspector**

---

## 7. Interpreting Collected Metrics

| Metric | Definition | Ideal Baseline Target | Notes |
| :--- | :--- | :--- | :--- |
| **Throughput (req/s)** | Total requests completed per second across all threads | Linear growth up to thread saturation | Indicates Gateway proxy capacity |
| **Average Latency (ms)** | Arithmetic mean response duration | $< 50$ ms on localhost | Measures end-to-end round trip |
| **p95 Latency (ms)** | 95th percentile latency | $< 100$ ms | Core SLA metric; 95% of users experience this or better |
| **p99 Latency (ms)** | 99th percentile latency | $< 250$ ms | Tail latency; sensitive to GC pauses and pool exhaustion |
| **HTTP Error Rate (%)** | Ratio of non-2xx/3xx responses to total requests | $0.00\%$ | Any errors indicate downstream bottlenecks or timeouts |
| **Total Requests** | Aggregate sample volume | $\sim 1000 - 15000+$ depending on duration | Determines statistical significance of percentiles |

---

## 8. Reproducing Experiments

To reproduce baseline measurements in a clean state:
1. Stop all existing Spring Boot processes on ports 8080, 8081, 8082, 8083.
2. Ensure database state is pristine (or allow seed data initialization).
3. Re-launch applications with baseline flags (`-DRATE_LIMIT_ENABLED=false -DCACHE_ENABLED=false`).
4. Execute `run-baseline-benchmarks.ps1 -WorkloadLevel all`.
5. Compare the output CSV files across runs to assess reproducibility and variance.

