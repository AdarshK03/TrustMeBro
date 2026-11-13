# Database Design and Isolation Specification

## Cloud-Based API Gateway and Microservices Management Platform
**Target Database:** Neon PostgreSQL  
**Document Version:** 1.0.0  

---

## 1. Database Architecture & Microservice Data Ownership

### 1.1 Microservice Data Ownership Principle
In strict adherence to microservice patterns:
- **No Shared Database Tables:** Each service exclusively owns its schema, tables, and transactional state.
- **No Direct Cross-Service Queries or Foreign Keys Across Service Schemas:** An order records a `user_id` as an integer reference, but does NOT create an SQL `FOREIGN KEY` constraint pointing to the `users` table in another service's database. Cross-boundary integrity is enforced via application logic and API contracts.

### 1.2 Neon PostgreSQL Isolation Strategy
Neon provides serverless PostgreSQL branching and database isolation. In this platform, isolation is maintained using **Dedicated Schemas** (or dedicated logical databases within the Neon project):

- `users_schema` $\rightarrow$ Owned exclusively by **User Service**
- `products_schema` $\rightarrow$ Owned exclusively by **Product Service**
- `orders_schema` $\rightarrow$ Owned exclusively by **Order Service**

Each service connects using standard JDBC connection strings with the default schema search path set to its own domain schema, e.g.:
```text
jdbc:postgresql://<neon-endpoint>.neon.tech/<dbname>?currentSchema=users_schema&sslmode=require
```

### 1.3 Local Development & Fallback Strategy
To permit building, testing, and verifying the application skeleton without requiring live Neon credentials or internet connectivity:
- Applications configure a dual-profile or spring datasource configuration.
- When `SPRING_DATASOURCE_URL` is omitted or set to local fallback, the services utilize an in-memory or embedded database (such as H2 in PostgreSQL compatibility mode) or load `schema.sql` automatically.
- No live database credentials are hardcoded into source files.

---

## 2. Entity Specifications

### 2.1 User Service (`users_schema.users`)

Represents registered platform accounts.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGSERIAL` / `BIGINT` | `PRIMARY KEY` | Auto-incrementing unique user ID |
| `name` | `VARCHAR(100)` | `NOT NULL` | Full name of the user |
| `email` | `VARCHAR(150)` | `NOT NULL UNIQUE` | Unique email address (used for login) |
| `role` | `VARCHAR(30)` | `NOT NULL DEFAULT 'CUSTOMER'` | Role: `CUSTOMER`, `ADMIN`, `SUPPORT` |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Record creation timestamp (UTC) |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Record last update timestamp (UTC) |

**Indexes:**
- `idx_users_email`: `CREATE UNIQUE INDEX idx_users_email ON users(email);`

---

### 2.2 Product Service (`products_schema.products`)

Represents catalog items, pricing, and stock levels.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGSERIAL` / `BIGINT` | `PRIMARY KEY` | Auto-incrementing product ID |
| `name` | `VARCHAR(150)` | `NOT NULL` | Name of the product |
| `description` | `TEXT` | `NULL` | Long-form product description |
| `price` | `NUMERIC(10, 2)` | `NOT NULL CHECK (price > 0)` | Unit price in USD (must be positive) |
| `stock` | `INTEGER` | `NOT NULL DEFAULT 0 CHECK (stock >= 0)` | Available physical inventory count |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Record last update timestamp |

**Indexes:**
- `idx_products_name`: `CREATE INDEX idx_products_name ON products(name);`

---

### 2.3 Order Service (`orders_schema.orders`)

Represents transactional customer purchase orders.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGSERIAL` / `BIGINT` | `PRIMARY KEY` | Unique order identifier |
| `user_id` | `BIGINT` | `NOT NULL` | Logical reference to User Service `user.id` (No cross-DB FK) |
| `status` | `VARCHAR(30)` | `NOT NULL DEFAULT 'CREATED'` | Status: `CREATED`, `CONFIRMED`, `CANCELLED` |
| `total_amount` | `NUMERIC(12, 2)` | `NOT NULL DEFAULT 0.00` | Sum total of line items in order |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Placement timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Last status modification timestamp |

**Indexes:**
- `idx_orders_user_id`: `CREATE INDEX idx_orders_user_id ON orders(user_id);`
- `idx_orders_status`: `CREATE INDEX idx_orders_status ON orders(status);`

---

### 2.4 Order Service (`orders_schema.order_items`)

Represents individual product line items within an order.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGSERIAL` / `BIGINT` | `PRIMARY KEY` | Line item identifier |
| `order_id` | `BIGINT` | `NOT NULL REFERENCES orders(id) ON DELETE CASCADE` | Intra-service Foreign Key to parent order |
| `product_id` | `BIGINT` | `NOT NULL` | Logical reference to Product Service `product.id` |
| `quantity` | `INTEGER` | `NOT NULL CHECK (quantity > 0)` | Number of units ordered |
| `unit_price` | `NUMERIC(10, 2)` | `NOT NULL CHECK (unit_price >= 0)` | Snapshot price at time of order creation |
| `subtotal` | `NUMERIC(12, 2)` | `NOT NULL CHECK (subtotal >= 0)` | Calculated `quantity * unit_price` |

**Indexes:**
- `idx_order_items_order_id`: `CREATE INDEX idx_order_items_order_id ON order_items(order_id);`

---

## 3. SQL Schema Definition Scripts

### 3.1 User Service Schema (`user-service/src/main/resources/schema.sql`)
```sql
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    role VARCHAR(30) NOT NULL DEFAULT 'CUSTOMER',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
```

### 3.2 Product Service Schema (`product-service/src/main/resources/schema.sql`)
```sql
CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    price NUMERIC(10, 2) NOT NULL CHECK (price > 0),
    stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);
```

### 3.3 Order Service Schema (`order-service/src/main/resources/schema.sql`)
```sql
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0),
    subtotal NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0)
);

CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
```

---

## 4. Environment Configuration & Connection Templates

All connection credentials must be supplied via environment variables in production and staging.

```bash
# Example template for User Service connection
USER_DB_URL=jdbc:postgresql://ep-example-123456.us-east-2.aws.neon.tech/neondb?sslmode=require&currentSchema=users_schema
USER_DB_USERNAME=neon_user
USER_DB_PASSWORD=your_secure_password_here

# Example template for Product Service connection
PRODUCT_DB_URL=jdbc:postgresql://ep-example-123456.us-east-2.aws.neon.tech/neondb?sslmode=require&currentSchema=products_schema
PRODUCT_DB_USERNAME=neon_user
PRODUCT_DB_PASSWORD=your_secure_password_here

# Example template for Order Service connection
ORDER_DB_URL=jdbc:postgresql://ep-example-123456.us-east-2.aws.neon.tech/neondb?sslmode=require&currentSchema=orders_schema
ORDER_DB_USERNAME=neon_user
ORDER_DB_PASSWORD=your_secure_password_here
```
