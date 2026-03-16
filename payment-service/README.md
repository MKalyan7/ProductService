# Payment Service

A production-style Payment Service microservice for the e-commerce platform, handling payment lifecycle management with state machine enforcement.

## Architecture

```
                    +------------------+
                    |   API Gateway    |
                    |    (port 8080)   |
                    +--------+---------+
                             |
         +-------------------+-------------------+
         |                   |                   |
+--------v--------+ +-------v---------+ +-------v---------+
| Product Service  | | Order Service   | | Payment Service |
|   (port 8081)    | |  (port 8082)    | |  (port 8083)    |
+--------+---------+ +-------+---------+ +-------+---------+
         |                   |                   |
         +-------------------+-------------------+
                             |
                    +--------v---------+
                    |     MongoDB      |
                    |   (port 27017)   |
                    +------------------+
```

## Technology Stack

- Java 17
- Spring Boot 3.2.2
- Spring Data MongoDB
- Gradle (Groovy DSL)
- SpringDoc OpenAPI / Swagger
- Docker + Docker Compose
- JUnit 5 + Testcontainers

## Payment Lifecycle

```
INITIATED ---> PROCESSING ---> SUCCESS ---> REFUNDED
    |               |
    +---> FAILED    +---> FAILED
    |
    +---> SUCCESS ---> REFUNDED
```

### State Transition Rules

| From | To | Allowed |
|------|----|---------|
| INITIATED | PROCESSING | Yes |
| INITIATED | SUCCESS | Yes |
| INITIATED | FAILED | Yes |
| PROCESSING | SUCCESS | Yes |
| PROCESSING | FAILED | Yes |
| SUCCESS | REFUNDED | Yes |
| FAILED | * | No (terminal) |
| REFUNDED | * | No (terminal) |

## API Endpoints

### Payments (`/api/v1/payments`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/payments` | Create a new payment (INITIATED) |
| `GET` | `/api/v1/payments/{paymentId}` | Get payment by ID |
| `GET` | `/api/v1/payments` | List payments (pagination, filters) |
| `POST` | `/api/v1/payments/{paymentId}/process` | Move to PROCESSING |
| `POST` | `/api/v1/payments/{paymentId}/success` | Mark as SUCCESS |
| `POST` | `/api/v1/payments/{paymentId}/fail` | Mark as FAILED |
| `POST` | `/api/v1/payments/{paymentId}/refund` | Refund a SUCCESS payment |
| `GET` | `/api/v1/payments/order/{orderId}` | Get payments by order |
| `GET` | `/api/v1/payments/internal/order/{orderId}/latest` | Get latest payment for order (internal) |

### Admin Seed (`/api/v1/admin/seed`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/admin/seed/payments` | Seed sample payment data |
| `GET` | `/api/v1/admin/seed/payments/status` | Get seed status |

### Query Parameters for List Payments

| Parameter | Default | Description |
|-----------|---------|-------------|
| `customerId` | - | Filter by customer ID |
| `orderId` | - | Filter by order ID |
| `status` | - | Filter by payment status |
| `page` | 0 | Page number (0-indexed) |
| `size` | 10 | Page size |
| `sort` | createdAt | Sort field |
| `sortDir` | desc | Sort direction (asc/desc) |

## Running Locally

### Option 1: Docker Compose (Recommended)

```bash
# From the project root
docker compose up --build
```

All services start automatically: MongoDB, product-service (8081), order-service (8082), payment-service (8083), api-gateway (8080).

### Option 2: Standalone

Prerequisites: Java 17, MongoDB running on localhost:27017

```bash
cd payment-service
chmod +x gradlew
./gradlew bootRun
```

## URLs

| Resource | URL |
|----------|-----|
| Payment Service | http://localhost:8083 |
| Swagger UI | http://localhost:8083/swagger-ui.html |
| API Docs | http://localhost:8083/api-docs |
| Health Check | http://localhost:8083/actuator/health |
| Via Gateway | http://localhost:8080/api/v1/payments/** |

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8083 | Service port |
| `spring.data.mongodb.database` | paymentdb | Database name |
| `app.seed.endpoint.enabled` | false | Enable/disable seed endpoints |
| `app.seed.default-count` | 500 | Default seed record count |

## Package Structure

```
com.paymentservice
  +-- controller/        # REST controllers
  +-- service/           # Business logic & state transitions
  +-- repository/        # MongoDB repositories
  +-- entity/            # Domain entities & enums
  +-- dto/
  |   +-- request/       # Request DTOs with validation
  |   +-- response/      # Response DTOs
  +-- exception/         # Custom exceptions & global handler
  +-- config/            # OpenAPI, MongoDB config
  +-- filter/            # Correlation ID filter
  +-- seed/              # Data seeding service
  +-- util/              # Payment ID generator
```

## Testing

```bash
# Run all tests (43 tests)
./gradlew test

# Run with report
./gradlew test --info
```

Test coverage includes:
- Service layer unit tests (business rules, state transitions)
- Controller layer tests (MockMvc, validation, error handling)
- Repository integration tests (Testcontainers + MongoDB)
- Application context loading test

## Order Service Integration

**Chosen approach: Option A** - Payment Service is built as a standalone service. Order Service is left untouched.

The internal endpoint `GET /api/v1/payments/internal/order/{orderId}/latest` is available for future order-service integration.

### Future Integration Path

1. Order Service adds a `PaymentServiceClient` (WebClient/RestTemplate)
2. After payment success, order-service calls confirm on the order
3. Later: Replace REST calls with Kafka events (PAYMENT_SUCCESS, PAYMENT_FAILED, etc.)

## Future Enhancements

- **Kafka Events**: PAYMENT_CREATED, PAYMENT_SUCCESS, PAYMENT_FAILED, PAYMENT_REFUNDED
- **External Payment Gateway**: Integration with Stripe/PayPal
- **Idempotency Keys**: Prevent duplicate payment processing
- **Saga Pattern**: Distributed transaction management
- **Auth/Security**: JWT-based authentication
- **Refund Workflows**: Partial refunds, refund approval flow

## Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| PAYMENT_NOT_FOUND | 404 | Payment does not exist |
| INVALID_PAYMENT_STATE | 400 | Invalid state transition |
| DUPLICATE_PAYMENT | 409 | Duplicate payment ID |
| VALIDATION_ERROR | 400 | Request validation failure |
| INTERNAL_ERROR | 500 | Unexpected server error |
