# Acceptance Criteria Specification

## Cloud-Based API Gateway and Microservices Management Platform
**Document Version:** 1.0.0  
**Target Milestone:** Day 1 Completion  

---

## 1. Overall System Acceptance Criteria

- [ ] **AC-SYS-01: Modular Workspace Structure**
  - The workspace contains clearly partitioned directories: `gateway/`, `user-service/`, `product-service/`, `order-service/`, `frontend/`, `docs/`, and `scripts/`.
  - No code or controllers are merged into a shared monolithic application.

- [ ] **AC-SYS-02: Independent Buildability**
  - Each Spring Boot application compiles cleanly using Maven (`mvn clean compile` or `mvn test-compile`) within its own folder.
  - An optional root aggregator POM allows building all four services together with a single command without forcing tightly coupled dependencies.

- [ ] **AC-SYS-03: Port Allocation Discipline**
  - Port configurations are strictly non-conflicting:
    - Gateway: `8080`
    - User Service: `8081`
    - Product Service: `8082`
    - Order Service: `8083`
    - Frontend: `5173` (Vite) / `3000` (Production preview)

- [ ] **AC-SYS-04: Credential & Secret Cleanliness**
  - No cleartext passwords, database credentials, or secret keys exist in committed code or configuration files.
  - A comprehensive `.env.example` file is provided with placeholder templates.
  - A root `.gitignore` excludes `.env`, `target/`, `node_modules/`, `*.log`, and IDE metadata.

- [ ] **AC-SYS-05: Health Check Standard**
  - Every Spring Boot application exposes `/actuator/health`.
  - When invoked, the health endpoint responds with HTTP 200 and a JSON payload indicating `{"status": "UP"}`.

- [ ] **AC-SYS-06: Unified Error Envelope**
  - All REST endpoints return a structured error response on validation failures (HTTP 400) or missing resources (HTTP 404):
    ```json
    {
      "timestamp": "2026-10-24T12:00:00Z",
      "status": 404,
      "error": "Not Found",
      "message": "User with id 99 not found",
      "path": "/api/users/99",
      "requestId": "corr-uuid-1234"
    }
    ```

- [ ] **AC-SYS-07: Mandatory Task Boundary Compliance**
  - No Google Cloud projects or accounts have been created.
  - No remote Git repositories have been created.
  - No Dockerfiles or Docker Compose files have been introduced.
  - No deployment scripts for Google Cloud Run have been generated.

---

## 2. API Gateway Acceptance Criteria (`gateway`)

- [ ] **AC-GW-01: Reactive Stack Integrity**
  - The Gateway is built purely with Spring Cloud Gateway and Spring WebFlux.
  - The blocking servlet starter `spring-boot-starter-web` is completely absent from `gateway/pom.xml`.

- [ ] **AC-GW-02: Route Definition & Delegation**
  - Gateway routes traffic according to path patterns:
    - Requests matching `/api/users/**` route to `http://localhost:8081/api/users/**`.
    - Requests matching `/api/products/**` route to `http://localhost:8082/api/products/**`.
    - Requests matching `/api/orders/**` route to `http://localhost:8083/api/orders/**`.

- [ ] **AC-GW-03: Correlation Header Injection**
  - The Gateway inspects incoming requests for an `X-Correlation-ID` header.
  - If missing, it generates a unique UUID and attaches it to both downstream request headers and external response headers.

- [ ] **AC-GW-04: Fallback & Gateway Health**
  - Exposes `GET /actuator/health` responding with `{"status":"UP"}`.
  - Exposes a fallback endpoint `/fallback/service-unavailable` returning HTTP 503 with standardized error envelope.

---

## 3. User Service Acceptance Criteria (`user-service`)

- [x] **AC-USR-01: REST API Contracts**
  - `POST /api/users` accepts `{"name": "...", "email": "...", "role": "..."}` and returns HTTP 201 Created with the persisted user object.
  - `GET /api/users` returns HTTP 200 OK with a list of users.
  - `GET /api/users/{id}` returns HTTP 200 OK with the target user, or HTTP 404 Not Found if missing.

- [x] **AC-USR-02: Payload Validation**
  - Rejects blank name (`HTTP 400 Bad Request`).
  - Rejects invalid email syntax (`HTTP 400 Bad Request`).
  - Validation error response lists specific field errors in standardized error envelope.

- [x] **AC-USR-03: Data Isolation & Schema**
  - User Service has its own dedicated schema definition (`schema.sql`) isolated to the `users` table/schema.
  - Does not read or write to tables owned by Product or Order services.

- [x] **AC-USR-04: Duplicate Conflict Handling**
  - Rejects duplicate email addresses with `HTTP 409 Conflict` at both service and database constraint levels.

- [x] **AC-USR-05: Repeatable Seed Data**
  - Configurable seed data via `app.seed-data.enabled: ${APP_SEED_DATA_ENABLED:false}`.
  - Idempotently checks existence before persisting to ensure zero duplicates upon application restart.

- [x] **AC-USR-06: Automated Tests**
  - Isolated unit tests (`UserServiceUnitTest`) testing business logic with Mockito.
  - Comprehensive API integration tests (`UserControllerTest`) verifying HTTP status codes, validation, and database persistence.

---

## 4. Product Service Acceptance Criteria (`product-service`)

- [x] **AC-PRD-01: Full CRUD REST API Contracts**
  - `POST /api/products` accepts `{"name": "...", "description": "...", "price": 99.99, "stock": 50}` and returns HTTP 201 Created.
  - `GET /api/products` returns HTTP 200 OK with catalog items.
  - `GET /api/products/{id}` returns HTTP 200 OK or HTTP 404 Not Found.
  - `PUT /api/products/{id}` updates an existing product and returns HTTP 200 OK with updated details, or HTTP 404 if not found.
  - `DELETE /api/products/{id}` deletes a product and returns HTTP 204 No Content, or HTTP 404 if not found.

- [x] **AC-PRD-02: Payload Validation**
  - Rejects non-positive prices (`price <= 0` results in HTTP 400).
  - Rejects negative stock count (`stock < 0` results in HTTP 400).
  - Rejects blank product names (`HTTP 400 Bad Request`).

- [x] **AC-PRD-03: Data Isolation & Schema**
  - Product Service defines schema `schema.sql` for the `products` table.
  - Has zero compile-time or runtime dependencies on User or Order database schemas.

- [x] **AC-PRD-04: Non-Silent Failure Guarantee**
  - Updating or deleting a non-existent product throws `ResourceNotFoundException` resulting in HTTP 404 Not Found rather than silently reporting success.

- [x] **AC-PRD-05: Repeatable Seed Data**
  - Configurable seed data via `app.seed-data.enabled: ${APP_SEED_DATA_ENABLED:false}`.
  - Idempotently verifies product non-existence before inserting sample catalog records.

- [x] **AC-PRD-06: Automated Tests**
  - Isolated unit tests (`ProductServiceUnitTest`) testing business logic with Mockito.
  - Comprehensive API integration tests (`ProductControllerTest`) verifying all CRUD endpoints, validation constraints, and database mutations.

---

## 5. Order Service Acceptance Criteria (`order-service`)

- [ ] **AC-ORD-01: REST API Contracts**
  - `POST /api/orders` accepts `{"userId": 1, "items": [{"productId": 10, "quantity": 2}]}` and returns HTTP 201 Created with generated `orderId`, calculated `totalAmount`, and timestamp.
  - `GET /api/orders` returns HTTP 200 OK with order summaries.
  - `GET /api/orders/{id}` returns HTTP 200 OK with order details and nested items.

- [ ] **AC-ORD-02: Payload Validation**
  - Requires valid `userId` (positive non-null).
  - Requires non-empty `items` list with positive `quantity > 0`.

- [ ] **AC-ORD-03: Service Communication Boundary**
  - Contains client abstractions (`ProductServiceClient`, `UserServiceClient`) defining the contract for validating user existence and retrieving live product prices.
  - Day 1 provides graceful mock/stub fallback implementations for standalone execution.

---

## 6. React Frontend Acceptance Criteria (`frontend`)

- [ ] **AC-UI-01: Buildable & Runnable**
  - Frontend installs dependencies with `npm install` without deprecation blockers.
  - Frontend compiles to static bundle via `npm run build` with zero errors.

- [ ] **AC-UI-02: Centralized API Client**
  - Implements `src/api/apiClient.js` reading `VITE_API_GATEWAY_URL` (defaulting to `http://localhost:8080`).
  - No hardcoded microservice URLs (`localhost:8081`, `localhost:8082`, etc.) in UI components.

- [ ] **AC-UI-03: Interactive Dashboard**
  - Displays service status overview for Gateway, User Service, Product Service, and Order Service.
  - Includes interactive route tester to fire requests through the Gateway and display structured responses.
