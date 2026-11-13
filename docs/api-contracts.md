# API Contracts and Route Specifications

## Cloud-Based API Gateway and Microservices Management Platform
**Document Version:** 1.0.0  
**Status:** Approved for Day 1 Implementation  

---

## 1. Gateway Routing Table

All external client traffic must be directed to the **API Gateway** on port `8080`. The Gateway rewrites/delegates paths downstream to the respective microservices:

| External Path Pattern | Target Microservice | Downstream Service URL | Gateway Route ID |
| :--- | :--- | :--- | :--- |
| `/api/users/**` | User Service | `http://localhost:8081` | `user-service-route` |
| `/api/products/**` | Product Service | `http://localhost:8082` | `product-service-route` |
| `/api/orders/**` | Order Service | `http://localhost:8083` | `order-service-route` |
| `/fallback/**` | API Gateway Internal | Local Fallback Handler | `fallback-route` |
| `/actuator/**` | Specific to Service or Gateway | Evaluated per instance | Actuator |

---

## 2. Standardized Error Response Structure

All services, including the Gateway, adhere to a standardized JSON error envelope on non-2xx responses.

### Schema (JSON)
```json
{
  "timestamp": "2026-10-24T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed human-readable error description or validation failure list",
  "path": "/api/users",
  "requestId": "a7b3c8d1-4e2f-4a8b-9012-3456789abcde"
}
```

### Fields
- `timestamp`: ISO-8601 UTC timestamp of error generation.
- `status`: Standard HTTP numeric status code (e.g. `400`, `401`, `404`, `500`).
- `error`: Canonical HTTP status text reason phrase (e.g. `Bad Request`, `Not Found`).
- `message`: Specific description of the error or field validation messages.
- `path`: The requested URI path.
- `requestId`: The correlation identifier passed via `X-Correlation-ID` header, enabling end-to-end tracing across gateway and backend logs.

---

## 3. User Service Endpoints (`user-service`)

**Base Prefix:** `/api/users`  
**Owning Service:** User Service (`http://localhost:8081`)  
**Gateway Route Mapping:** `/api/users/**` $\rightarrow$ `http://localhost:8081/api/users/**`  

### 3.1 Create User
- **Method & Path:** `POST /api/users`
- **Future Auth Required:** No (Public registration endpoint; will issue tokens on Day 4).
- **Request Headers:**
  - `Content-Type: application/json`
  - `X-Correlation-ID: <uuid>` (optional; generated if absent)
- **Request Body:**
  ```json
  {
    "name": "Jane Doe",
    "email": "jane.doe@example.com",
    "role": "CUSTOMER"
  }
  ```
- **Validation Rules:**
  - `name`: Required, non-blank, max 100 characters.
  - `email`: Required, valid email format, unique.
  - `role`: Optional string (default: `CUSTOMER`). Allowed: `CUSTOMER`, `ADMIN`.
- **Success Response (HTTP 201 Created):**
  ```json
  {
    "id": 1,
    "name": "Jane Doe",
    "email": "jane.doe@example.com",
    "role": "CUSTOMER",
    "createdAt": "2026-10-24T12:05:00Z",
    "updatedAt": "2026-10-24T12:05:00Z"
  }
  ```
- **HTTP Status Codes:**
  - `201 Created`: User successfully created.
  - `400 Bad Request`: Validation failure (blank name or invalid email syntax).
  - `409 Conflict`: Email already exists.

### 3.2 List Users
- **Method & Path:** `GET /api/users`
- **Future Auth Required:** Yes (Day 4: Admin role required).
- **Query Parameters:** None for Day 1 (future: `?page=0&size=20`).
- **Success Response (HTTP 200 OK):**
  ```json
  [
    {
      "id": 1,
      "name": "Jane Doe",
      "email": "jane.doe@example.com",
      "role": "CUSTOMER",
      "createdAt": "2026-10-24T12:05:00Z"
    }
  ]
  ```
- **HTTP Status Codes:**
  - `200 OK`: List retrieved successfully (may be empty `[]`).

### 3.3 Get User by ID
- **Method & Path:** `GET /api/users/{id}`
- **Future Auth Required:** Yes (Day 4: Self or Admin).
- **Path Parameters:**
  - `id`: Numeric Long (e.g., `1`).
- **Success Response (HTTP 200 OK):**
  ```json
  {
    "id": 1,
    "name": "Jane Doe",
    "email": "jane.doe@example.com",
    "role": "CUSTOMER",
    "createdAt": "2026-10-24T12:05:00Z",
    "updatedAt": "2026-10-24T12:05:00Z"
  }
  ```
- **HTTP Status Codes:**
  - `200 OK`: User found.
  - `404 Not Found`: User with specified ID does not exist.

---

## 4. Product Service Endpoints (`product-service`)

**Base Prefix:** `/api/products`  
**Owning Service:** Product Service (`http://localhost:8082`)  
**Gateway Route Mapping:** `/api/products/**` $\rightarrow$ `http://localhost:8082/api/products/**`  

### 4.1 Create Product
- **Method & Path:** `POST /api/products`
- **Future Auth Required:** Yes (Day 4: Admin role required).
- **Request Body:**
  ```json
  {
    "name": "Mechanical Keyboard",
    "description": "Ergonomic RGB mechanical keyboard",
    "price": 89.99,
    "stock": 100
  }
  ```
- **Validation Rules:**
  - `name`: Required, non-blank, max 150 characters.
  - `price`: Required, numeric, strictly greater than 0.00.
  - `stock`: Required, integer, greater than or equal to 0.
- **Success Response (HTTP 201 Created):**
  ```json
  {
    "id": 1,
    "name": "Mechanical Keyboard",
    "description": "Ergonomic RGB mechanical keyboard",
    "price": 89.99,
    "stock": 100,
    "createdAt": "2026-10-24T12:10:00Z",
    "updatedAt": "2026-10-24T12:10:00Z"
  }
  ```
- **HTTP Status Codes:**
  - `201 Created`: Product created.
  - `400 Bad Request`: Validation failure (negative price or blank name).

### 4.2 List Products
- **Method & Path:** `GET /api/products`
- **Future Auth Required:** No (Public catalog browsing; candidate for Gateway response caching on Day 4).
- **Success Response (HTTP 200 OK):**
  ```json
  [
    {
      "id": 1,
      "name": "Mechanical Keyboard",
      "description": "Ergonomic RGB mechanical keyboard",
      "price": 89.99,
      "stock": 100
    }
  ]
  ```
- **HTTP Status Codes:**
  - `200 OK`: Products retrieved.

### 4.3 Get Product by ID
- **Method & Path:** `GET /api/products/{id}`
- **Future Auth Required:** No (Public catalog item).
- **Path Parameters:**
  - `id`: Numeric Long.
- **Success Response (HTTP 200 OK):**
  ```json
  {
    "id": 1,
    "name": "Mechanical Keyboard",
    "description": "Ergonomic RGB mechanical keyboard",
    "price": 89.99,
    "stock": 100,
    "createdAt": "2026-10-24T12:10:00Z",
    "updatedAt": "2026-10-24T12:10:00Z"
  }
  ```
- **HTTP Status Codes:**
  - `200 OK`: Found.
  - `404 Not Found`: Product not found.

### 4.4 Update Product
- **Method & Path:** `PUT /api/products/{id}`
- **Future Auth Required:** Yes (Day 4: Admin role required).
- **Path Parameters:**
  - `id`: Numeric Long.
- **Request Body:**
  ```json
  {
    "name": "Mechanical Keyboard Pro",
    "description": "Ergonomic RGB mechanical keyboard with hot-swappable switches",
    "price": 109.99,
    "stock": 80
  }
  ```
- **Validation Rules:**
  - `name`: Required, non-blank, max 150 characters.
  - `price`: Required, numeric, strictly greater than 0.00.
  - `stock`: Required, integer, greater than or equal to 0.
- **Success Response (HTTP 200 OK):**
  ```json
  {
    "id": 1,
    "name": "Mechanical Keyboard Pro",
    "description": "Ergonomic RGB mechanical keyboard with hot-swappable switches",
    "price": 109.99,
    "stock": 80,
    "createdAt": "2026-10-24T12:10:00Z",
    "updatedAt": "2026-10-27T10:00:00Z"
  }
  ```
- **HTTP Status Codes:**
  - `200 OK`: Product updated successfully.
  - `400 Bad Request`: Validation failure (blank name, price <= 0, stock < 0).
  - `404 Not Found`: Product with specified ID does not exist.

### 4.5 Delete Product
- **Method & Path:** `DELETE /api/products/{id}`
- **Future Auth Required:** Yes (Day 4: Admin role required).
- **Path Parameters:**
  - `id`: Numeric Long.
- **Success Response:**
  - `HTTP 204 No Content` (Empty response body).
- **HTTP Status Codes:**
  - `204 No Content`: Product deleted successfully.
  - `404 Not Found`: Product with specified ID does not exist.

---

## 5. Order Service Endpoints (`order-service`)

**Base Prefix:** `/api/orders`  
**Owning Service:** Order Service (`http://localhost:8083`)  
**Gateway Route Mapping:** `/api/orders/**` $\rightarrow$ `http://localhost:8083/api/orders/**`  

### 5.1 Create Order
- **Method & Path:** `POST /api/orders`
- **Future Auth Required:** Yes (Day 4: Authenticated user).
- **Request Body:**
  ```json
  {
    "userId": 1,
    "items": [
      {
        "productId": 1,
        "quantity": 2
      }
    ]
  }
  ```
- **Validation Rules:**
  - `userId`: Required, positive Long.
  - `items`: Required, non-empty array with at least 1 item.
  - `items[].productId`: Required, positive Long.
  - `items[].quantity`: Required, integer $\ge 1$.
- **Success Response (HTTP 201 Created):**
  ```json
  {
    "id": 1,
    "userId": 1,
    "status": "CREATED",
    "totalAmount": 179.98,
    "items": [
      {
        "id": 1,
        "productId": 1,
        "quantity": 2,
        "unitPrice": 89.99,
        "subtotal": 179.98
      }
    ],
    "createdAt": "2026-10-24T12:15:00Z"
  }
  ```
- **HTTP Status Codes:**
  - `201 Created`: Order placed.
  - `400 Bad Request`: Validation failure (empty items or invalid quantities).
  - `404 Not Found`: User ID or Product ID does not exist in upstream service.

### 5.2 List Orders
- **Method & Path:** `GET /api/orders`
- **Future Auth Required:** Yes (Day 4: User's orders or Admin).
- **Success Response (HTTP 200 OK):**
  ```json
  [
    {
      "id": 1,
      "userId": 1,
      "status": "CREATED",
      "totalAmount": 179.98,
      "createdAt": "2026-10-24T12:15:00Z"
    }
  ]
  ```
- **HTTP Status Codes:**
  - `200 OK`: Success.

### 5.3 Get Order by ID
- **Method & Path:** `GET /api/orders/{id}`
- **Future Auth Required:** Yes.
- **Success Response (HTTP 200 OK):**
  ```json
  {
    "id": 1,
    "userId": 1,
    "status": "CREATED",
    "totalAmount": 179.98,
    "items": [
      {
        "id": 1,
        "productId": 1,
        "quantity": 2,
        "unitPrice": 89.99,
        "subtotal": 179.98
      }
    ],
    "createdAt": "2026-10-24T12:15:00Z"
  }
  ```
- **HTTP Status Codes:**
  - `200 OK`: Success.
  - `404 Not Found`: Order not found.

---

## 6. Actuator & Monitoring Endpoints

Each application exposes the standard Spring Boot Actuator health endpoint:

| Service | Endpoint | Success Response |
| :--- | :--- | :--- |
| API Gateway | `GET http://localhost:8080/actuator/health` | `{"status":"UP"}` |
| User Service | `GET http://localhost:8081/actuator/health` | `{"status":"UP"}` |
| Product Service | `GET http://localhost:8082/actuator/health` | `{"status":"UP"}` |
| Order Service | `GET http://localhost:8083/actuator/health` | `{"status":"UP"}` |

Swagger / OpenAPI documentation is made available on:
- User Service: `http://localhost:8081/swagger-ui/index.html` (API docs at `/v3/api-docs`)
- Product Service: `http://localhost:8082/swagger-ui/index.html` (API docs at `/v3/api-docs`)
- Order Service: `http://localhost:8083/swagger-ui/index.html` (API docs at `/v3/api-docs`)
