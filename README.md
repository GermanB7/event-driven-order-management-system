# Event-Driven Order Management System

A backend portfolio project focused on event-driven workflow orchestration, eventual consistency, reliability patterns, and operational inspection.

This is not an e-commerce clone. The business domain is intentionally small: create an order, reserve inventory, authorize payment, request fulfillment, and compensate when the workflow fails. The interesting part is the backend design around asynchronous coordination, transactional messaging, idempotent consumers, retry behavior, DLQs, replay, and internal visibility.

## Why This Project Exists

This project demonstrates backend engineering decisions that are difficult to show in a simple CRUD API:

- Coordinating a multi-step workflow without synchronous service coupling.
- Publishing domain events reliably using a transactional outbox.
- Handling duplicate delivery and event replay safely.
- Making asynchronous workflows inspectable when something fails.
- Keeping a modular monolith structured enough for future extraction.

The scope is bounded on purpose. Orders, inventory, payments, and shipping are modeled only as much as needed to exercise workflow and reliability concerns.

## How It Complements My Neobank Backend

A Neobank backend usually highlights account modeling, financial transactions, balances, validations, and synchronous API correctness.

This project complements that by showing a different backend skill set:

- Event-driven orchestration instead of primarily request/response flows.
- Eventual consistency instead of single-transaction business operations.
- Outbox, Kafka, retry, DLQ, idempotency, and replay instead of only direct database writes.
- Operational inspection for asynchronous workflows instead of only resource-level API reads.
- Modular monolith boundaries designed with future service extraction in mind.

Together, the two projects show both transactional backend discipline and distributed-systems-oriented workflow design.

## Backend Engineering Signals

- Modular monolith with explicit package boundaries.
- Orchestrated saga in the `workflow` module.
- Transactional outbox for reliable event publication.
- Kafka-based asynchronous event delivery.
- Idempotent consumers backed by processed-message tables.
- Retry and DLQ configuration per consumer group/module.
- Outbox replay endpoint for eligible events.
- Internal workflow inspection endpoints.
- Flyway-managed PostgreSQL schema.
- Micrometer, Actuator, and Prometheus metrics.
- Architecture tests and end-to-end workflow tests.

## High-Level Architecture

```text
POST /api/orders
      |
      v
orders module
      |
      | writes order + ORDER_CREATED outbox event in one transaction
      v
outbox.outbox_event
      |
      | scheduled relay publishes pending events
      v
Kafka topic: order-events
      |
      v
workflow module orchestrates the saga
      |
      +--> inventory module reserves or rejects stock
      |
      +--> payments module authorizes or fails payment
      |
      +--> shipping module prepares fulfillment
      |
      v
orders module confirms, cancels, or marks fulfillment requested
```

The system runs as a modular monolith, but modules communicate through events and explicit application boundaries rather than direct cross-module orchestration.

## Module Overview

| Module | Responsibility |
|---|---|
| `orders` | Public order API, order aggregate, order state transitions |
| `inventory` | Stock reservation and release |
| `payments` | Payment authorization result modeling |
| `shipping` | Shipment preparation state |
| `workflow` | Saga orchestration across order, inventory, payment, and shipping events |
| `messaging` | Event envelope, event types, processed-message store |
| `outbox` | Outbox relay, Kafka publishing, replay support |
| `observability` | Internal workflow timeline, status aggregate, failures, summary |
| `shared` | Minimal shared configuration and primitives |

## Core Workflow

Happy path:

```text
ORDER_CREATED
  -> INVENTORY_RESERVATION_REQUESTED
  -> INVENTORY_RESERVED
  -> PAYMENT_AUTHORIZATION_REQUESTED
  -> PAYMENT_AUTHORIZED
  -> ORDER_CONFIRMED
  -> SHIPMENT_PREPARATION_REQUESTED
  -> SHIPMENT_PREPARATION_STARTED
```

Expected final state:

- Order: `FULFILLMENT_REQUESTED`
- Inventory reservation: `RESERVED`
- Payment: `AUTHORIZED`
- Shipment: `PREPARING`

This path is covered by `FulfillmentWorkflowIntegrationTest`.

## Failure And Compensation Flow

Payment failure compensation path:

```text
ORDER_CREATED
  -> INVENTORY_RESERVATION_REQUESTED
  -> INVENTORY_RESERVED
  -> PAYMENT_AUTHORIZATION_REQUESTED
  -> PAYMENT_FAILED
  -> INVENTORY_RELEASE_REQUESTED
  -> INVENTORY_RELEASED
  -> ORDER_CANCELLED
```

Expected final state:

- Order: `CANCELLED`
- Payment: `FAILED`
- Inventory reservation: `RELEASED`

This path is covered by `PaymentFailureCompensationIntegrationTest`.

Inventory rejection is also modeled through `INVENTORY_RESERVATION_REJECTED`, which allows the workflow to cancel when stock cannot be reserved.

## Reliability And Messaging

### Transactional Outbox

The order API writes business state and the first integration event in the same database transaction. Events are stored in `outbox.outbox_event`.

The outbox relay claims pending events, publishes them to Kafka, and updates event status. Relay settings include batch size, retry delay, claim TTL, and max retries.

### Kafka

The main topic is:

```text
order-events
```

Kafka is used as the asynchronous transport between workflow listeners and domain module listeners.

### Idempotent Consumers

Consumers use processed-message tracking so replayed or redelivered events do not cause duplicate state changes. The project includes processed-message storage under `messaging.processed_messages`, with inventory-specific processed-message support as well.

### Retry And DLQ

Kafka listeners are configured with retry/backoff and dead-letter publishing. DLQ topics include:

```text
order-events.orders.dlq
order-events.workflow.dlq
order-events.payment.dlq
order-events.shipping.dlq
order-events.inventory.reservation.dlq
order-events.inventory.release.dlq
```

The outbox relay also tracks failed publication attempts and can mark events as dead-lettered after max retries.

### Replay

The internal replay endpoint can request replay for eligible outbox events:

```http
POST /internal/outbox/events/{eventId}/replay?requestedBy=internal-ops
```

Replay is intentionally scoped to the outbox event lifecycle. This project does not implement a full Kafka DLQ browser or operator console.

## Observability And Internal Inspection

Internal endpoints expose workflow and operational state:

| Endpoint | Purpose |
|---|---|
| `GET /internal/orders/{orderId}/workflow` | Timeline of outbox events for an order |
| `GET /internal/orders/{orderId}/status` | Aggregated order, inventory, payment, shipment, and event state |
| `GET /internal/workflows?workflowId={workflowId}` | Events for a workflow id |
| `GET /internal/events/{eventId}` | Event lookup by event id |
| `GET /internal/failures` | Failed or dead-lettered async operations |
| `GET /internal/summary` | Operational summary counts |
| `POST /internal/outbox/events/{eventId}/replay` | Request outbox replay for an eligible event |

Actuator endpoints are also exposed:

```text
/actuator/health
/actuator/metrics
/actuator/prometheus
```

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Web
- Spring Kafka
- Spring Data JPA
- JDBC/JdbcTemplate for operational persistence paths
- PostgreSQL 16
- Kafka 3.8
- Flyway
- Micrometer, Actuator, Prometheus registry
- JUnit 5, Spring Boot Test, Embedded Kafka, H2
- ArchUnit

## Run Locally

Start infrastructure:

```powershell
docker compose up -d
```

Run the application:

```powershell
.\mvnw.cmd spring-boot:run
```

The application starts on:

```text
http://localhost:8080
```

Useful local endpoints:

```text
http://localhost:8080/actuator/health
http://localhost:8080/swagger-ui/index.html
```

## Local Demo

The local database needs inventory stock before the workflow can reserve `SKU-1`.

Seed inventory:

```powershell
docker exec -i edoms-postgres psql -U order_user -d order_management -c "insert into inventory.inventory_items (sku, available_quantity, reserved_quantity, updated_at) values ('SKU-1', 10, 0, now()) on conflict (sku) do update set available_quantity = excluded.available_quantity, reserved_quantity = excluded.reserved_quantity, updated_at = now();"
```

Create an order:

```powershell
curl.exe -i -X POST http://localhost:8080/api/orders `
  -H "Content-Type: application/json" `
  -d "{\"customerId\":\"11111111-1111-1111-1111-111111111111\",\"currency\":\"USD\",\"items\":[{\"sku\":\"SKU-1\",\"quantity\":2,\"unitPrice\":10.00}]}"
```

Inspect the order:

```powershell
curl.exe http://localhost:8080/api/orders/{orderId}
```

Inspect workflow and operational state:

```powershell
curl.exe http://localhost:8080/internal/orders/{orderId}/workflow
curl.exe http://localhost:8080/internal/orders/{orderId}/status
curl.exe http://localhost:8080/internal/summary
```

## Demo Scenarios

### 1. Happy Path

Goal: show the complete asynchronous chain from API request to shipment preparation.

```powershell
.\mvnw.cmd test -Dtest=FulfillmentWorkflowIntegrationTest
```

Demonstrates HTTP order creation, transactional outbox write, Kafka publication, workflow orchestration, inventory reservation, payment authorization, order confirmation, shipment preparation, and final persisted state checks.

### 2. Payment Failure Compensation

Goal: show failure handling and compensating actions.

```powershell
.\mvnw.cmd test -Dtest=PaymentFailureCompensationIntegrationTest
```

Demonstrates payment failure detection, compensation event emission, inventory release, order cancellation, and final consistency across modules.

### 3. Idempotency And Replay Safety

Goal: show that replayed events do not create duplicate processing.

```powershell
.\mvnw.cmd test -Dtest=IdempotencyE2EIntegrationTest
```

Demonstrates duplicate/replayed event handling, processed-message based idempotency, and stable final state after replay.

## Testing Strategy

The project uses multiple test layers:

- Domain and use-case tests for business state transitions.
- Listener tests for message handling behavior.
- Integration tests for persistence and internal endpoints.
- Architecture tests for module boundary enforcement.
- E2E tests for real async workflow evidence using MockMvc, outbox relay, Embedded Kafka, and polling assertions.

Primary evidence tests:

```powershell
.\mvnw.cmd test -Dtest=FulfillmentWorkflowIntegrationTest,PaymentFailureCompensationIntegrationTest,IdempotencyE2EIntegrationTest
```

Full test suite:

```powershell
.\mvnw.cmd test
```

## Architecture Decisions And Tradeoffs

### Modular Monolith First

The project uses a modular monolith to keep local development and portfolio review simple while still enforcing boundaries. This avoids premature microservice complexity while preserving a credible extraction path.

### Orchestrated Saga

The `workflow` module owns cross-module coordination. Order creation accepts the command and emits the first event; the workflow module decides the next step.

### Transactional Outbox

The outbox avoids the classic failure mode where database state commits but the integration event is lost before publication.

### Eventual Consistency

The workflow does not pretend every step is synchronous. State becomes consistent as events are published and consumed.

### Internal Inspection Over A UI

The project exposes internal JSON endpoints instead of building an admin frontend. That keeps the focus on backend operability and avoids unnecessary product scope.

## Supporting Documentation

- `docs/architecture/overview.md` - architecture, modules, workflow, reliability patterns
- `docs/demo-runbook.md` - local run commands and demo scenarios
- `docs/interview-notes.md` - concise talking points and tradeoffs
- `docs/adr/0001-modular-monolith-boundaries.md` - modular monolith decision
- `docs/adr/0002-saga-orchestration-and-outbox.md` - saga and outbox decision

## Future Improvements

These are intentionally left out of the current scope:

- Dedicated operator UI for workflow timelines and replay.
- Full Kafka DLQ browser and replay tooling.
- Distributed tracing across event handlers.
- Load and resilience testing.
- Service extraction of inventory, payments, or shipping modules.
- More realistic payment/shipping provider integrations.

## Interview Talking Points

- Why the project is a modular monolith instead of microservices.
- How the outbox protects event publication.
- How the saga moves through happy-path and compensation events.
- Where idempotency is enforced and why it matters.
- What happens when a consumer fails.
- What the internal inspection endpoints reveal during debugging.
- Which parts are intentionally simplified to keep the project focused.
- How specific modules could be extracted later if operational needs justified it.
