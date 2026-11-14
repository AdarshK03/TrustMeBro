# Cloud-Based API Gateway and Microservices Management Platform

**Implementation Phase:** Day 1 Skeleton, Requirements & Architecture Foundation  
**Target Milestone:** Day 1 Completion of Seven-Day Plan  

---

## 1. Project Objective

The **Cloud-Based API Gateway and Microservices Management Platform** is a decoupled, modern cloud-ready microservices architecture. It demonstrates:
- Centralized ingress management and path-based dynamic routing via **Spring Cloud Gateway**.
- Decentralized microservices adhering strictly to single-responsibility and data-ownership isolation.
- Traceability through end-to-end `X-Correlation-ID` header injection.
- Independent service execution, buildability, and testing.
- A single-page **React** frontend communicating exclusively through the Gateway.
- Persistence designed for managed **Neon PostgreSQL** serverless databases.

---

## 2. Technology Stack & Compatibility Matrix

| Tier / Component | Technology | Version | Purpose |
| :--- | :--- | :--- | :--- |
| **Java Platform** | OpenJDK / JBR | 17+ (JDK 21 / 25 compatible) | Core language runtime |
| **Backend Framework** | Spring Boot | `3.3.4` | Microservice container |
| **API Gateway** | Spring Cloud Gateway | `2023.0.3` (Gateway 4.1.5) | Reactive WebFlux reverse proxy |
| **Database** | Neon PostgreSQL | PostgreSQL 16+ | Serverless relational persistence |
| **Fallback DB** | H2 Database | Latest (Runtime scope) | Local offline zero-dependency profile |
| **Build Tool** | Apache Maven | 3.9+ | Java dependency & build management |
| **Testing** | JUnit 5 & Spring Test | Latest with MockMvc | Automated unit and integration testing |
| **API Docs** | Springdoc OpenAPI / Swagger | `2.6.0` | Living REST API documentation |
| **Frontend** | React + Vite | React 18.3, Vite 5.4 | Single Page Application dashboard |

---

## 3. Workspace Directory Structure

```text
TrustMeBro/
├── docs/                               # Architectural and design documentation
│   ├── requirements.md                 # System requirements and boundaries
│   ├── architecture.md                 # Architecture, Mermaid diagrams, flows
│   ├── api-contracts.md                # Route specifications and error envelope
│   ├── database-design.md              # Data models, schema isolation, SQL
│   └── acceptance-criteria.md          # Testable criteria per component
├── gateway/                            # Spring Cloud Gateway (Port 8080)
│   ├── pom.xml
│   └── src/
├── user-service/                       # User Service (Port 8081)
│   ├── pom.xml
│   └── src/
├── product-service/                    # Product Service (Port 8082)
│   ├── pom.xml
│   └── src/
├── order-service/                      # Order Service (Port 8083)
│   ├── pom.xml
│   └── src/
├── frontend/                           # React + Vite application (Port 5173)
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   └── src/
├── scripts/                            # Operational automation scripts
│   ├── build-all.ps1                   # Builds all microservices and frontend
│   ├── test-all.ps1                    # Runs automated test suites
│   └── run-all.ps1                     # Launches all services in separate terminals
├── .env.example                        # Safe environment variable template
├── .gitignore                          # Clean version-control ignore rules
├── pom.xml                             # Aggregator parent POM
└── README.md                           # Reproducible documentation and setup guide
```

---

## 4. Port Allocations & Gateway Route Table

### Port Topology
- **API Gateway:** `8080` (Primary public HTTP entry point)
- **User Service:** `8081` (Internal / Ingress via Gateway)
- **Product Service:** `8082` (Internal / Ingress via Gateway)
- **Order Service:** `8083` (Internal / Ingress via Gateway)
- **Frontend SPA:** `5173` (Vite Dev Server)

### Gateway Routing Table
External clients query `http://localhost:8080`:

| Ingress Route Pattern | Target Microservice | Forwarded Destination | Correlation Filter |
| :--- | :--- | :--- | :--- |
| `/api/users/**` | User Service | `http://localhost:8081` | Injects `X-Correlation-ID` |
| `/api/products/**` | Product Service | `http://localhost:8082` | Injects `X-Correlation-ID` |
| `/api/orders/**` | Order Service | `http://localhost:8083` | Injects `X-Correlation-ID` |
| `/fallback/**` | API Gateway Internal | Local Fallback Controller | Generates HTTP 503 envelope |

---

## 5. Health Check & Documentation Endpoints

| Service | Health Check URL | OpenAPI / Swagger UI |
| :--- | :--- | :--- |
| **API Gateway** | `http://localhost:8080/actuator/health` | N/A (Edge Router) |
| **User Service** | `http://localhost:8081/actuator/health` | `http://localhost:8081/swagger-ui/index.html` |
| **Product Service** | `http://localhost:8082/actuator/health` | `http://localhost:8082/swagger-ui/index.html` |
| **Order Service** | `http://localhost:8083/actuator/health` | `http://localhost:8083/swagger-ui/index.html` |

---

## 6. Prerequisites

- **Java Development Kit (JDK):** Version 17, 21, or 25.
- **Maven:** Version 3.8+ (or bundled in IntelliJ IDEA).
- **Node.js & npm:** Node 18+ and npm 9+.

---

## 7. Environment Configuration & Neon PostgreSQL Integration

The platform includes built-in automatic `.env` discovery via `DotenvEnvironmentPostProcessor` in every microservice.
When any service boots (via Maven, JAR, IDE, or PowerShell scripts), it traverses up the directory tree to locate `.env` and injects its variables into the Spring `Environment` prior to configuration binding.

1. Ensure `.env` is present in the project root with your Neon PostgreSQL credentials:
   ```properties
   USER_DB_URL=jdbc:postgresql://<neon-host>:5432/neondb?sslmode=require&channel_binding=require
   USER_DB_USERNAME=neondb_owner
   USER_DB_PASSWORD=<neon-password>
   PRODUCT_DB_URL=jdbc:postgresql://<neon-host>:5432/neondb?sslmode=require&channel_binding=require
   PRODUCT_DB_USERNAME=neondb_owner
   PRODUCT_DB_PASSWORD=<neon-password>
   ORDER_DB_URL=jdbc:postgresql://<neon-host>:5432/neondb?sslmode=require&channel_binding=require
   ORDER_DB_USERNAME=neondb_owner
   ORDER_DB_PASSWORD=<neon-password>
   ```
2. **Neon Database Auto-Initialization:** When services connect to NeonDB, tables (`users`, `products`, `orders`, `order_items`) and indexes are automatically created via schema scripts and JPA DDL.
3. **Safe Test Isolation:** Automated test suites (`mvn test` / `.\scripts\test-all.ps1`) automatically use isolated in-memory H2 databases configured in `src/test/resources/application.yml` so tests run reliably without modifying production Neon data.
4. **Safe Local Fallback:** If `.env` is missing or variables are omitted, services safely fall back to local in-memory H2 database in PostgreSQL mode.

---

## 8. Building and Testing the Platform

### Option A: Build and Test Everything via Automation Scripts
```powershell
# Run all automated tests
.\scripts\test-all.ps1

# Build all microservices and frontend bundle
.\scripts\build-all.ps1
```

### Option B: Build All Spring Boot Apps via Root Maven POM
```bash
mvn clean package
```

### Option C: Build and Run Applications Independently

Each microservice is an autonomous Maven module:

#### 1. API Gateway
```bash
cd gateway
mvn clean test
mvn spring-boot:run
```

#### 2. User Service
```bash
cd user-service
mvn clean test
mvn spring-boot:run
```

#### 3. Product Service
```bash
cd product-service
mvn clean test
mvn spring-boot:run
```

#### 4. Order Service
```bash
cd order-service
mvn clean test
mvn spring-boot:run
```

#### 5. React Frontend
```bash
cd frontend
npm install
npm run dev
# Or create production build:
npm run build
```

---

## 9. Launching the Whole Platform Locally

To launch all four microservices and the React frontend in separate PowerShell console windows simultaneously:
```powershell
.\scripts\run-all.ps1
```

Visit the frontend dashboard in your browser:
```text
http://localhost:5173
```

---

## 10. Day 2 Service Implementation Details

### User Service (`user-service`)
- **Port:** `8081`
- **Endpoints:**
  - `POST /api/users`: Creates a user with validation (non-blank name, valid email format).
  - `GET /api/users`: Returns all users.
  - `GET /api/users/{id}`: Returns user by ID, or HTTP 404 Not Found.
- **Conflict Handling:** Duplicate email submissions trigger a domain `DuplicateResourceException` and database constraint translation returning `HTTP 409 Conflict`.
- **Seed Data:** `UserDataInitializer` optionally seeds 3 sample users when `APP_SEED_DATA_ENABLED=true` (`alice.dev@example.com`, `bob.cust@example.com`, `charlie.support@example.com`).

### Product Service (`product-service`)
- **Port:** `8082`
- **Endpoints:**
  - `POST /api/products`: Creates a product with validation (positive price `> 0`, non-negative stock `>= 0`, non-blank name). Returns HTTP 201 Created.
  - `GET /api/products`: Lists catalog items. Returns HTTP 200 OK.
  - `GET /api/products/{id}`: Retrieves product by ID. Returns HTTP 200 OK or 404 Not Found.
  - `PUT /api/products/{id}`: Updates an existing product. Returns HTTP 200 OK or 404 Not Found.
  - `DELETE /api/products/{id}`: Deletes a product. Returns HTTP 204 No Content or 404 Not Found.
- **Seed Data:** `ProductDataInitializer` optionally seeds 4 sample catalog products when `APP_SEED_DATA_ENABLED=true` (Mechanical Keyboard, 4K Monitor, Headphones, Wireless Mouse).

### Enabling Development Seed Data
To populate predictable sample records in development without creating duplicates on server restarts:
```bash
# Via environment variable:
export APP_SEED_DATA_ENABLED=true
# Or in PowerShell:
$env:APP_SEED_DATA_ENABLED="true"
# Or as a JVM argument:
mvn spring-boot:run -Dspring-boot.run.arguments="--app.seed-data.enabled=true"
```

---

## 11. Design Decisions & Subsequent Task Handover

- **Reactive Gateway Decision:** The API Gateway uses `spring-cloud-starter-gateway` (WebFlux/Netty). The servlet engine `spring-boot-starter-web` is strictly avoided in `gateway` to prevent class-path conflicts.
- **Data Isolation:** Each service owns its tables (`users`, `products`, `orders`, `order_items`). Cross-service tables are never joined via SQL; they are linked by logical entity IDs and verified via REST contracts.
- **Consistent Error Envelope:** RFC-compatible JSON envelope (`timestamp`, `status`, `error`, `message`, `path`, `requestId`) is centrally managed via `@RestControllerAdvice`.
- **Inter-Service Communication (Day 1):** `Order Service` includes interface definitions (`UserServiceClient`, `ProductServiceClient`) with resilient local fallback mocks so orders can be placed even if peer services are temporarily offline during development.
- **Day 3 & Day 4 Roadmap:** Gateway dynamic routing integration and Order Service business orchestration are scheduled for Day 3. JWT authentication filters and Redis-backed rate limiting are scheduled for Day 4.

---

## 13. Day 5 Implementation Details: React Interface, Test Harness & Baseline Benchmarking

### 13.1 Professional React Dashboard
A modular, high-contrast, responsive dashboard built with **React 18** and **Vite**:
- **Service Health Overview:** Displays cards for API Gateway (8080), User Service (8081), Product Service (8082), and Order Service (8083). Health checks are routed strictly through the Gateway via `/api/health/{service}` endpoints, maintaining architectural encapsulation without exposing downstream service ports to the browser. Includes per-service checks, response latency, and a global **Refresh All** action.
- **Product Management:** Complete catalog interface supporting `GET /api/products` listing, in-stock/low-stock inventory badges, detailed modal inspection (`GET /api/products/{id}`), new product creation (`POST /api/products`), and loading/empty/error states.
- **Order Management:** Purchase order management interface supporting `GET /api/orders`, detailed order inspection with nested line items (`GET /api/orders/{id}`), and an interactive order placement form (`POST /api/orders`) executing cross-service validations with user verification and product stock checks.
- **Standardized API Error Handling:** Dedicated `ErrorDisplay` component visualizing canonical error envelopes (`timestamp`, `status`, `error`, `message`, `path`, `requestId`, `correlationId`). Handles HTTP 400 (validation), 401 (unauthorized), 403 (forbidden), 404 (not found), 409 (conflict), 429 (rate limited), 500/503 (server error), and status 0 (Gateway unavailable / network error) with actionable user guidance.
- **Performance Experiments Interface:** Load test evaluation tool supporting import of CSV and JSON benchmark results generated by Apache JMeter. Automatically calculates and displays Average Latency, p95 Latency, p99 Latency, Throughput (req/s), Total Requests, Success/Failure counts, and Error Rate. Includes a per-endpoint breakdown table and a raw sample inspector.
- **Centralized API Client (`frontend/src/api/apiClient.js`):** Centralizes all HTTP traffic to `VITE_API_GATEWAY_URL` (defaulting to `http://localhost:8080`), automatically propagating correlation IDs, parsing JSON responses, handling network failures, and supporting JWT authentication tokens.

### 13.2 JMeter Load Testing Harness
Located under `scripts/load-testing/`:
- **Test Plan (`api-gateway-baseline.jmx`):** Standardized Apache JMeter 5.6.3+ test plan targeting Gateway port 8080. Evaluates representative endpoints (`GET /api/products`, `GET /api/users`, `GET /api/orders`) with request pacing and correlation headers.
- **Workload Configurations:**
  - `low-workload.properties`: 5 concurrent virtual users, 10s ramp-up, 60s duration.
  - `medium-workload.properties`: 20 concurrent virtual users, 20s ramp-up, 120s duration.
  - `high-workload.properties`: 50 concurrent virtual users, 30s ramp-up, 180s duration.
- **Benchmark Runner (`run-baseline-benchmarks.ps1`):** Automates service readiness verification, endpoint validation, and headless execution for Apache JMeter (or fallback high-concurrency PowerShell runspace harness), writing standard CSV results and summary JSON metrics to `scripts/load-testing/results/baseline/`.
- **Benchmarking Guide (`scripts/load-testing/README.md`):** Comprehensive documentation for JMeter setup, application startup, workload execution, metric interpretation, and experiment reproduction.

---

## 14. Day 6 Implementation Details: Optimized Experiments, Performance Comparison & Results Analysis

### 14.1 Four Controlled Gateway Experimental Configurations
To evaluate the isolated and combined performance impacts of API Gateway optimizations, four distinct operational configurations are supported via runtime configuration switches:
- **Configuration A — Baseline:** Gateway routing enabled, Rate limiting disabled (`-DRATE_LIMIT_ENABLED=false`), Response caching disabled (`-DCACHE_ENABLED=false`).
- **Configuration B — Rate Limiting Only:** Gateway routing enabled, Rate limiting enabled (`-DRATE_LIMIT_ENABLED=true -DRATE_LIMIT_MAX_REQUESTS=100 -DRATE_LIMIT_WINDOW_SECONDS=60`), Response caching disabled. Requests exceeding the threshold return `HTTP 429 Too Many Requests` with `Retry-After` headers.
- **Configuration C — Response Caching Only:** Gateway routing enabled, Rate limiting disabled, Response caching enabled (`-DCACHE_ENABLED=true -DCACHE_EXPIRATION_SECONDS=60`). Safely caches idempotent `GET /api/products` and `GET /api/products/{id}` calls; automatically invalidates cache upon product mutations (`POST`, `PUT`, `DELETE`).
- **Configuration D — Combined Optimizations:** Gateway routing enabled, Rate limiting enabled, Response caching enabled. Rate limiting executes first at `HIGHEST_PRECEDENCE + 10` to shed abusive traffic, followed by caching at `HIGHEST_PRECEDENCE + 30` to serve allowed queries at sub-2ms latency.

### 14.2 Gateway Optimization Telemetry & Diagnostics
- **Diagnostic Endpoint:** Exposes `GET /api/gateway/stats` providing real-time telemetry on active rate-limit status, tracked client IPs, cache hit count, miss count, total lookups, cache hit ratio percentage, invalidation count, and estimated cache memory size.
- **Cache & Rate-Limit Reset:** Provides `POST /api/gateway/cache/reset` and `POST /api/gateway/rate-limit/reset` to reset counters and evict entries between repeated test runs.

### 14.3 Multi-Run Experiment Harness & Python Analytical Suite
- **Experiment Runner (`scripts/load-testing/run-experiments.ps1`):** Supports running Baseline, Rate-Limit-Only, Cache-Only, or Combined configurations across Low (5 users), Medium (20 users), and High (50 users) workloads for at least 3 repetitions (`run1`, `run2`, `run3`). Isolates `HTTP 429` rejections from backend failures and queries live Gateway telemetry.
- **Analysis Tool (`scripts/load-testing/analysis/analyze_results.py`):** Automatically reads raw CSV/JSON results, calculates mean, p95, p99, throughput, error rates, and cache hit ratios, and aggregates across runs.
- **Visual Chart Generator:** Generates standalone vector SVG comparison charts under `scripts/load-testing/analysis/comparison/` for Average Latency, p95 Latency, p99 Latency, Throughput, HTTP Error Rate, and Cache Hit Ratio.
- **Findings Report (`scripts/load-testing/analysis/experimental_findings.md`):** Comprehensive report covering trade-offs, stability findings, and the seven mandatory experimental limitations.

### 14.4 Extended React Performance Results Interface
- **Multi-Configuration Comparison View:** Allows viewing Baseline, Rate Limiting Only, Cache Only, and Combined results side-by-side.
- **Workload Filters:** Interactive pills to filter comparison tables and charts by Low, Medium, High, or All workloads.
- **Integrated SVG Chart Visualizer:** Interactive bar charts rendered directly in React comparing configurations across latency, throughput, error rate, and cache hit ratio.
- **Full Matrix Import:** Seamlessly imports `comparison_summary.json` to populate all configurations and workloads with a single file upload.
- **Live Gateway Telemetry:** Interactive panel displaying active Gateway optimization status queried via `GET /api/gateway/stats`.

---

## 15. Mandatory Student-Owned Task Boundaries (Confirmed Untouched)

Antigravity has strictly respected the four required boundaries:
1. **Google Cloud Account / Project:** Not created.
2. **GitHub / Remote Git Repository:** Not created.
3. **Containerization & Docker:** No Dockerfiles or Docker Compose files generated.
4. **Cloud Run / Cloud Deployment:** No deployment scripts or cloud deployment executed.
