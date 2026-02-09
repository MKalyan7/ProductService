# Product Service Microservice

A complete Product Service microservice built with Spring Boot 3.x, MongoDB, and comprehensive REST APIs for managing products, categories, and inventory.

<!-- Dummy change for testing PR workflow -->

## Tech Stack

- Java 17
- Spring Boot 3.2.2
- Spring Web
- Spring Data MongoDB
- Spring Validation
- Spring Boot Actuator
- OpenAPI/Swagger (springdoc-openapi)
- Gradle
- JUnit 5, Mockito, Testcontainers

## Project Structure

```
product-service/
├── src/
│   ├── main/
│   │   ├── java/com/productservice/
│   │   │   ├── config/          # Configuration classes
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── dto/             # Request/Response DTOs
│   │   │   ├── entity/          # MongoDB entities
│   │   │   ├── exception/       # Exception handling
│   │   │   ├── filter/          # Request filters
│   │   │   ├── repository/      # MongoDB repositories
│   │   │   ├── seed/            # Data seeding
│   │   │   └── service/         # Business logic
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-docker.yml
│   └── test/
│       └── java/com/productservice/
│           ├── integration/     # Integration tests
│           └── service/         # Unit tests
├── build.gradle
├── Dockerfile
├── docker-compose.yml
└── README.md
```

## Running Locally

### Prerequisites

- Java 17+
- Gradle 8.x
- MongoDB running on localhost:27017

### Build

```bash
cd product-service
./gradlew clean build
```

### Run

```bash
./gradlew bootRun
```

The service will start on port 8081.

### Run with Seed Data

```bash
./gradlew bootRun --args='--app.seed=true'
```

## Running with Docker

### Build and Run

```bash
cd product-service
docker compose up --build
```

This will start:
- MongoDB on port 27017
- Product Service on port 8081 (with seed data enabled)

### Stop

```bash
docker compose down
```

### Stop and Remove Volumes

```bash
docker compose down -v
```

## API Endpoints

Base path: `/api/v1`

### Products

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /products | Create a new product |
| PUT | /products/{productId} | Update a product |
| GET | /products/{productId} | Get product by ID |
| GET | /products | List products with pagination |
| GET | /products/sku/{sku} | Get product by SKU |
| PATCH | /products/{productId}/deactivate | Deactivate a product |

Query parameters for listing products:
- `page` - Page number (default: 0)
- `size` - Page size (default: 10)
- `sort` - Sort field
- `sortDir` - Sort direction (asc/desc)
- `categoryId` - Filter by category
- `active` - Filter by active status
- `q` - Search query for name/description
- `minPrice` - Minimum price filter
- `maxPrice` - Maximum price filter

### Categories

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /categories | Create a new category |
| GET | /categories | List all categories |
| GET | /categories/{categoryId} | Get category by ID |
| PUT | /categories/{categoryId} | Update a category |

### Inventory

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /inventory/{productId} | Get inventory for product |
| PUT | /inventory/{productId} | Update stock quantity |
| POST | /inventory/{productId}/reserve?qty= | Reserve stock |
| POST | /inventory/{productId}/release?qty= | Release reserved stock |

## Sample cURL Commands

### Create a Category

```bash
curl -X POST http://localhost:8081/api/v1/categories \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Electronics",
    "description": "Electronic devices and gadgets"
  }'
```

### Create a Product

```bash
curl -X POST http://localhost:8081/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "ELEC-001",
    "name": "Wireless Headphones",
    "description": "High-quality wireless headphones",
    "categoryId": "<category-id>",
    "price": 99.99,
    "currency": "USD",
    "active": true,
    "initialStockQty": 100
  }'
```

### Get Product by ID

```bash
curl http://localhost:8081/api/v1/products/<product-id>
```

### List Products with Filters

```bash
curl "http://localhost:8081/api/v1/products?page=0&size=10&active=true&minPrice=50"
```

### Reserve Inventory

```bash
curl -X POST "http://localhost:8081/api/v1/inventory/<product-id>/reserve?qty=5"
```

### Release Inventory

```bash
curl -X POST "http://localhost:8081/api/v1/inventory/<product-id>/release?qty=3"
```

## Observability

### Actuator Endpoints

- Health: http://localhost:8081/actuator/health
- Info: http://localhost:8081/actuator/info

### Swagger UI

- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI Docs: http://localhost:8081/api-docs

### Correlation ID

All requests include a correlation ID for tracing:
- If `X-Correlation-Id` header is provided, it will be used
- Otherwise, a UUID is generated
- The correlation ID is included in response headers and logs

## Error Response Format

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "path": "/api/v1/products",
  "errorCode": "PRODUCT_NOT_FOUND",
  "message": "Product not found with ID: xyz",
  "details": ["additional details if any"]
}
```

## Data Seeding

The service includes a robust data generation feature for testing pagination, search, and filtering with realistic data.

### Seed Data Overview

The seeding feature generates:
- 30 categories (Electronics, Kitchen, Clothing, etc.)
- Configurable number of products (default: 1500)
- Inventory records for every product

Data characteristics:
- Realistic product names using brand + adjective + product type combinations
- Category-appropriate price ranges (e.g., Electronics: $50-$500, Grocery: $2-$15)
- ~10% inactive products
- ~10% out-of-stock, ~15% low-stock inventory distribution
- Timestamps spread across the last 18 months

### Enabling the Seed Endpoint

The seed endpoint is disabled by default for security. Enable it by setting:

```yaml
app:
  seed:
    endpoint:
      enabled: true
    default-count: 1500
```

Or via environment variable:
```bash
APP_SEED_ENDPOINT_ENABLED=true ./gradlew bootRun
```

Note: The `app.seed.enabled` property controls the legacy CommandLineRunner seeder (for Docker startup), while `app.seed.endpoint.enabled` controls the admin REST endpoint.

### Admin Seed Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/v1/admin/seed | Seed database with test data |
| GET | /api/v1/admin/seed/status | Get current seed status |

### Running the Seed Endpoint

```bash
curl -X POST "http://localhost:8081/api/v1/admin/seed?count=1500&seed=42&reset=true"
```

Parameters:
- `count` - Number of products to generate (default: 1500)
- `seed` - Random seed for deterministic generation (default: current timestamp)
- `reset` - If true, clears existing data before seeding (default: false)

### Check Seed Status

```bash
curl http://localhost:8081/api/v1/admin/seed/status
```

Response:
```json
{
  "categoryCount": 30,
  "productCount": 1500,
  "inventoryCount": 1500,
  "lastSeedRun": {
    "lastSeedTime": "2024-01-15T10:30:00Z",
    "seed": 42,
    "productCount": 1500,
    "categoryCount": 30,
    "durationMs": 2500,
    "status": "SUCCESS",
    "message": "Seeded 30 categories, 1500 products, 1500 inventories"
  }
}
```

### Idempotency

The seeding uses upsert operations based on SKU for products and categoryId for categories. This means:
- Re-running with `reset=false` will skip if data exists
- Re-running with `reset=true` will clear and reseed
- Same seed value produces identical data (deterministic)

### Performance

Expected seeding times (approximate):
- 500 products: ~1 second
- 1500 products: ~2-3 seconds
- 2000 products: ~3-4 seconds

Bulk inserts use chunks of 300 documents to balance memory usage and performance.

### Sample cURL Commands for Testing Seeded Data

After seeding, test the APIs:

```bash
# List products with pagination (sorted by price descending)
curl "http://localhost:8081/api/v1/products?page=0&size=20&sort=price&sortDir=desc"

# Search products by name/description
curl "http://localhost:8081/api/v1/products?q=wireless"

# Filter by category and price range (replace <categoryId> with actual ID)
curl "http://localhost:8081/api/v1/products?categoryId=<categoryId>&minPrice=50&maxPrice=200"

# Get product by SKU
curl "http://localhost:8081/api/v1/products/sku/ELEC-00001"

# Check inventory for a product
curl "http://localhost:8081/api/v1/inventory/<productId>"

# List all categories
curl "http://localhost:8081/api/v1/categories"
```

## Running Tests

### All Tests

```bash
./gradlew test
```

### Unit Tests Only

```bash
./gradlew test --tests "com.productservice.service.*"
```

### Integration Tests Only

```bash
./gradlew test --tests "com.productservice.integration.*"
```

Note: Integration tests use Testcontainers and require Docker to be running.

## Configuration

### application.yml (Local)

```yaml
server:
  port: 8081

spring:
  data:
    mongodb:
      host: localhost
      port: 27017
      database: productdb

app:
  seed:
    enabled: false
    endpoint:
      enabled: false
    default-count: 1500
```

### application-docker.yml (Docker)

```yaml
spring:
  data:
    mongodb:
      host: mongodb
      port: 27017
      database: productdb

app:
  seed:
    enabled: true
    endpoint:
      enabled: true
    default-count: 1500
```
