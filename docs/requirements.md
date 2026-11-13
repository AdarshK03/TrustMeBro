# Requirements Specification

## Cloud-Based API Gateway and Microservices Management Platform
**Document Version:** 1.0.0  
**Phase:** Day 1 Skeleton & Architecture Foundation  
**Target Environment:** Local Development & Neon PostgreSQL  

---

## 1. System Overview

The **Cloud-Based API Gateway and Microservices Management Platform** is a decoupled, cloud-ready microservices architecture designed to demonstrate enterprise API management, intelligent traffic routing, resilience, independent service scalability, and centralized observability.

Clients (such as the React single-page frontend or third-party consumers) do not communicate directly with backend services. Instead, all ingress traffic flows through an **API Gateway**, which routes requests to specialized backend microservices:

1. **API Gateway** (`gateway`)
2. **User Service** (`user-service`)
3. **Product Service** (`product-service`)
4. **Order Service** (`order-service`)

Persistence is handled by a managed **Neon PostgreSQL** database cluster, adhering strictly to microservice data isolation principles.

---

## 2. Functional Requirements (Day 1 Minimum Viable System)

### 2.1 API Gateway
- **Central Entry Point:** Act as the single public HTTP entry point for external client applications on port `8080`.
- **Dynamic Routing:** Route requests matching defined path prefixes to their respective microservices:
  - `/api/users/**` $\rightarrow$ User Service (`http://localhost:8081`)
  - `/api/products/**` $\rightarrow$ Product Service (`http://localhost:8082`)
  - `/api/orders/**` $\rightarrow$ Order Service (`http://localhost:8083`)
- **Request Correlation:** Generate and propagate a unique `X-Correlation-ID` header across downstream calls if none is provided.
- **Health & Readiness:** Expose `/actuator/health` and basic system diagnostic endpoints.
- **Architectural Boundary for Day 4:** Define clean hook points and filter chains for:
  - JWT token validation and role-based route protection.
  - Token-bucket rate limiting (Redis-backed or in-memory).
  - Response caching for idempotent read routes.
  - Gateway-level fallback controllers and circuit breakers.

### 2.2 User Service
- **Service Identity:** Runs independently on port `8081`.
- **Data Ownership:** Exclusively owns user identity records, profile data, and credentials.
- **API Capabilities (Day 1 Skeleton):**
  - `POST /api/users` — Register a new user with validation (email format, required name, role).
  - `GET /api/users` — List active users (with pagination stub).
  - `GET /api/users/{id}` — Retrieve user profile by unique identifier.
- **Error Handling:** Return structured RFC 7807-compatible error responses on invalid payloads or missing entities.
- **Health Check:** Expose `/actuator/health` to verify service availability and database connectivity.

### 2.3 Product Service
- **Service Identity:** Runs independently on port `8082`.
- **Data Ownership:** Exclusively owns product catalog data, SKU records, pricing, and available stock inventory.
- **API Capabilities (Day 1 Skeleton):**
  - `POST /api/products` — Create a new catalog product with validation (positive price, non-blank name, initial stock).
  - `GET /api/products` — List catalog products.
  - `GET /api/products/{id}` — Retrieve a single product by identifier.
- **Error Handling:** Standardized error contracts for duplicate SKUs, invalid currency values, or not found errors.
- **Health Check:** Expose `/actuator/health`.

### 2.4 Order Service
- **Service Identity:** Runs independently on port `8083`.
- **Data Ownership:** Exclusively owns order headers, order line items, order status lifecycles, and transaction history.
- **API Capabilities (Day 1 Skeleton):**
  - `POST /api/orders` — Create a new order referencing an existing user ID and a list of product items with quantities.
  - `GET /api/orders` — List orders.
  - `GET /api/orders/{id}` — Retrieve order details including calculated totals and line items.
- **Inter-Service Communication Boundary:**
  - Order creation requires validating user existence (via User Service) and product availability/pricing (via Product Service).
  - For Day 1, client contract interfaces and mock fallback clients are established to preserve loose coupling without crashing when peer services are offline.
- **Health Check:** Expose `/actuator/health`.

### 2.5 React Frontend
- **SPA Architecture:** Lightweight, responsive React application running on port `3000` (or `5173` via Vite).
- **Central Gateway Target:** All HTTP requests target the API Gateway base URL (`http://localhost:8080`) configured via environment variable `VITE_API_GATEWAY_URL`.
- **Day 1 Features:**
  - Project architecture overview and service topology dashboard.
  - Gateway route verification panel (allowing interactive testing of User, Product, Order routes through the Gateway).
  - Service health monitor card displaying real-time ping results for `/actuator/health` across all components.

---

## 3. Non-Functional Requirements

| Metric / Dimension | Requirement |
| :--- | :--- |
| **Independence** | Each microservice and the gateway must be independently buildable (`mvn clean package`) and runnable without requiring a unified monolith. |
| **Startup Resilience** | Services must handle missing live database credentials gracefully in local development by falling back to in-memory H2 profiles or failing fast with clear error logs. |
| **Data Decoupling** | No service may directly access or mutate tables owned by another service. Cross-service data needs must use REST APIs or events. |
| **Observability** | Every HTTP request passing through the gateway must have a traceable correlation ID attached in logs and response headers. |
| **Port Allocations** | Gateway: `8080`, User Service: `8081`, Product Service: `8082`, Order Service: `8083`, Frontend: `5173`/`3000`. |
| **Standardized Error Format** | Unified JSON payload with `timestamp`, `status`, `error`, `message`, `path`, and `requestId`. |
| **Security Baseline** | Zero hardcoded credentials in source control; all sensitive tokens and passwords injected through environment variables. |

---

## 4. Scope Boundaries & Seven-Day Plan Alignment

### In-Scope for Day 1
- Documented requirements, architecture, API contracts, acceptance criteria, and database schemas.
- Working Maven project skeleton for all 4 Spring Boot services with correct dependencies.
- Working React + Vite frontend skeleton with centralized API client.
- Spring Cloud Gateway configuration with reactive routing to the 3 downstream services.
- Basic CRUD/Read endpoint skeletons with DTO validation.
- Spring Boot Actuator health endpoints on all backend apps.
- Safe `.env.example` templates and `.gitignore` hygiene.
- Local compilation and unit test execution.

### Explicitly Out-of-Scope for Day 1
- **Payments:** Payment processing, credit card tokens, Stripe/PayPal webhooks are out of scope.
- **Distributed Tracing Infra:** Zipkin, Jaeger, OpenTelemetry collectors are not required for Day 1.
- **Enterprise Orchestration:** Kubernetes manifests, Helm charts, Service Meshes (Istio) are strictly avoided.
- **Full Security Implementation:** Day 4 will implement Spring Security + JWT authentication and Redis rate limiting.
- **Mandatory Exclusions (Student-Owned):**
  - No Google Cloud account or GCP project creation.
  - No GitHub or remote Git repository creation.
  - No Docker containerization, Dockerfiles, or Docker Compose files.
  - No deployment to Google Cloud Run or cloud hosting.
