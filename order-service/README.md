# Order Service

A Spring Boot microservice for managing orders with product-service integration for product validation, pricing, and inventory management.

## Tech Stack

- Java 17
- Spring Boot 3.2.2
- Spring Data MongoDB
- Spring WebFlux (WebClient for HTTP calls)
- Gradle (Groovy DSL)
- Testcontainers + MockWebServer for testing
- SpringDoc OpenAPI / Swagger UI

## Prerequisites

- Java 17+
- MongoDB running on `localhost:27017`
- Product Service running on `localhost:8081` (configurable)
- Docker (for Testcontainers-based tests and Docker deployment)

## Build & Test

```bash
# Build and run all tests
./gradlew clean test

# Build without tests
./gradlew clean build -x test

# Run the application
./gradlew bootRun
```

## Docker Deployment

```bash
# Start all services (MongoDB, product-service, order-service)
docker compose up --build

# Stop services
docker compose down

# Stop and remove volumes
docker compose down -v
```

### Exposed Ports (order-service stack)

These ports are chosen to avoid conflicts with the standalone product-service stack, allowing both to run simultaneously.

| Service                      | Host Port | Container Port | Notes                                    |
|------------------------------|-----------|----------------|------------------------------------------|
| order-service                | 8082      | 8082           |                                          |
| product-service (order stack)| 8083      | 8081           | Different host port to avoid conflict    |
| order-mongodb                | 27018     | 27017          |                                          |
| product-mongodb (order stack)| 27019     | 27017          | Different host port to avoid conflict    |

> **Note:** The product-service standalone stack uses ports `8081` and `27017`. The order-service stack maps its copies to `8083` and `27019` on the host so both stacks can run at the same time. Inside the Docker network, services communicate on their container ports (e.g. `product-service:8081`).

## Configuration

| Property                          | Default                    | Description                      |
|-----------------------------------|----------------------------|----------------------------------|
| `server.port`                     | `8082`                     | Server port                      |
| `spring.data.mongodb.host`        | `localhost`                | MongoDB host                     |
| `spring.data.mongodb.port`        | `27017`                    | MongoDB port                     |
| `spring.data.mongodb.database`    | `orderdb`                  | MongoDB database name            |
| `product.service.base-url`        | `http://localhost:8081`    | Product service base URL         |
| `product.service.connect-timeout-ms` | `3000`                  | WebClient connection timeout     |
| `product.service.read-timeout-ms` | `5000`                     | WebClient read timeout           |
| `app.seed.endpoint.enabled`       | `false`                    | Enable admin seed endpoints      |
| `app.seed.default-count`          | `2000`                     | Default number of orders to seed |
| `app.seed.reserve-inventory`      | `false`                    | Call product-service to reserve inventory during seeding |

## API Endpoints

### Swagger UI
- **URL**: [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8082/api-docs](http://localhost:8082/api-docs)

### Actuator
- **Health**: [http://localhost:8082/actuator/health](http://localhost:8082/actuator/health)
- **Info**: [http://localhost:8082/actuator/info](http://localhost:8082/actuator/info)

## Curl Examples

### Create an Order

```bash
curl -X POST http://localhost:8082/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: test-corr-001" \
  -d '{
    "customerId": "CUST-1001",
    "currency": "USD",
    "items": [
      {"productId": "<product-uuid>", "quantity": 2},
      {"productId": "<product-uuid-2>", "quantity": 1}
    ]
  }'
```

### Get Order by ID

```bash
curl http://localhost:8082/api/v1/orders/{orderId} \
  -H "X-Correlation-Id: test-corr-002"
```

### List Orders by Customer (Paged)

```bash
curl "http://localhost:8082/api/v1/orders?customerId=CUST-1001&page=0&size=10&sort=createdAt,desc"
```

### Confirm an Order

```bash
curl -X POST http://localhost:8082/api/v1/orders/{orderId}/confirm \
  -H "X-Correlation-Id: test-corr-003"
```

### Cancel an Order

```bash
curl -X POST http://localhost:8082/api/v1/orders/{orderId}/cancel \
  -H "X-Correlation-Id: test-corr-004"
```

### Check Health

```bash
curl http://localhost:8082/actuator/health
```

## Data Seeding

The order-service includes an admin endpoint for generating realistic test orders using real products from product-service.

### Prerequisites
- product-service must be running and seeded with products
- Enable the seed endpoint: set `app.seed.endpoint.enabled=true` in `application.yml` or via env var

### Enable Seed Endpoint

```yaml
app:
  seed:
    endpoint:
      enabled: true
```

Or via environment variable:
```bash
APP_SEED_ENDPOINT_ENABLED=true ./gradlew bootRun
```

### Seed Orders

```bash
curl -X POST "http://localhost:8082/api/v1/admin/seed/orders?count=2000&seed=42&reset=true&reserveInventory=false"
```

Parameters:
| Parameter          | Default | Description                                                      |
|--------------------|---------|------------------------------------------------------------------|
| `count`            | 2000    | Number of orders to generate (clamped to 500-3000)               |
| `seed`             | current time | Random seed for deterministic generation                    |
| `reset`            | false   | If true, deletes existing seeded orders before re-seeding        |
| `reserveInventory` | false   | If true, calls product-service reserve endpoint for each item    |

### Check Seed Status

```bash
curl http://localhost:8082/api/v1/admin/seed/status
```

### Verify Seeded Data

```bash
# List orders for a seeded customer
curl "http://localhost:8082/api/v1/orders?customerId=CUST-1050&page=0&size=10&sort=createdAt,desc"

# Get a specific order (use an orderId from the list response)
curl http://localhost:8082/api/v1/orders/{orderId}
```

### Seeding Design
- Fetches active products from product-service via `GET /api/v1/products?active=true` (paginated)
- Generates ~200 customers (CUST-1001 to CUST-1200)
- Each order has 1-5 items with random quantities 1-4
- Unit prices come from product-service (not invented)
- `createdAt` spread over the last 6 months
- Status distribution: ~70% CONFIRMED, ~20% CREATED, ~10% CANCELLED
- Orders are tagged with `dataTag: "seed-v1"` for idempotent reset
- Inventory reservation is configurable (`reserveInventory` param); if enabled and a reserve fails, it retries up to 3 times with a different product

## Order Lifecycle

```
CREATED  ──▶  CONFIRMED
   │
   └──────▶  CANCELLED (releases reserved inventory)
```

## Create Order Flow

1. Validate the request DTO
2. For each item:
   - Call product-service to verify product exists and is active
   - Retrieve unit price from product-service (do NOT trust client price)
3. Reserve inventory for each item via product-service
   - If any reservation fails, release all previously reserved items (compensation)
4. Calculate totals and persist order with status `CREATED`

## Error Response Format

```json
{
  "timestamp": "2025-01-01T00:00:00Z",
  "path": "/api/v1/orders",
  "errorCode": "PRODUCT_NOT_FOUND",
  "message": "Product not found: <productId>",
  "details": []
}
```

### Error Codes
| Code                         | HTTP Status | Description                         |
|------------------------------|-------------|-------------------------------------|
| `VALIDATION_ERROR`           | 400         | Request validation failed            |
| `ORDER_NOT_FOUND`            | 404         | Order does not exist                 |
| `INVALID_ORDER_STATE`        | 409         | Invalid state transition             |
| `PRODUCT_NOT_FOUND`          | 404         | Product not found in product-service |
| `PRODUCT_INACTIVE`           | 400         | Product is inactive                  |
| `OUT_OF_STOCK`               | 400         | Insufficient inventory               |
| `PRODUCT_SERVICE_UNAVAILABLE`| 502         | Product service is down/unreachable  |

## Assumptions

1. Product-service is available at the configured base URL and provides the documented API endpoints.
2. Product prices are always fetched from product-service at order creation time (client-provided prices are ignored).
3. Inventory reservation is synchronous and best-effort; if a release call fails during rollback, it is logged but does not fail the operation.
4. MongoDB auto-index creation is enabled for development; in production, indexes should be managed via migration scripts.
5. The `currency` field defaults to `"USD"` if not provided.
6. Order cancellation only releases inventory for orders in `CREATED` status.
7. Confirmation transitions `CREATED` to `CONFIRMED` without additional side effects.
