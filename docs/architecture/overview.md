# Architecture Overview

This project is a modular monolith that demonstrates event-driven workflow design without pretending to be a full commerce platform. The core concern is the backend workflow: an order moves through inventory reservation, payment authorization, fulfillment preparation, and compensation when a step fails.

## Structure

The codebase is organized by backend capability:

| Module | Responsibility |
|---|---|
| `orders` | Public order API, order aggregate, persisted order state |
| `inventory` | Stock reservation, rejection, and release |
| `payments` | Payment authorization result modeling |
| `shipping` | Shipment preparation state |
| `workflow` | Orchestrated saga coordination |
| `messaging` | Event envelope, event types, idempotency store |
| `outbox` | Transactional outbox relay, Kafka publishing, replay |
| `observability` | Internal inspection endpoints and operational summaries |
| `shared` | Minimal shared configuration |

The project intentionally stays as a modular monolith. It keeps development and review simple while still showing the boundaries that would matter if selected modules were extracted later.

## Workflow Model

Order creation is the only public business command. The order module persists the order and writes an `ORDER_CREATED` outbox event in the same transaction. After that, the workflow proceeds asynchronously through Kafka.

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

Payment failure compensation:

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

Inventory rejection is modeled separately with `INVENTORY_RESERVATION_REJECTED`, allowing the workflow to cancel when stock cannot be reserved.

## Reliability Patterns

### Transactional Outbox

Business state and integration events are committed together. The relay later claims pending outbox rows, publishes them to Kafka, and marks them published. Failed publication attempts are retried and can become dead-lettered after the configured retry limit.

### Kafka

The main event stream is `order-events`. Domain and workflow listeners consume from this topic and ignore event types they do not own.

### Idempotency

Consumers record processed event ids by consumer name. If the same event is delivered again, the consumer can detect the duplicate and avoid applying the same state transition twice.

### Retry And DLQ

Kafka listeners use module-specific retry/backoff settings and dead-letter topics. DLQs are configured for orders, workflow, payment, shipping, inventory reservation, and inventory release listeners.

### Replay

The outbox replay endpoint can requeue eligible outbox events. Replay is deliberately scoped to the outbox lifecycle; the project does not include a full Kafka DLQ management UI.

## Operational Inspection

The `observability` module exposes internal JSON endpoints:

| Endpoint | Purpose |
|---|---|
| `GET /internal/orders/{orderId}/workflow` | Event timeline for an order |
| `GET /internal/orders/{orderId}/status` | Aggregate order, inventory, payment, shipment, and event state |
| `GET /internal/workflows?workflowId={workflowId}` | Events for a workflow id |
| `GET /internal/events/{eventId}` | Event lookup by event id |
| `GET /internal/failures` | Failed or dead-lettered async operations |
| `GET /internal/summary` | Operational summary counts |
| `POST /internal/outbox/events/{eventId}/replay` | Request replay for an eligible outbox event |

These endpoints are internal inspection tools, not public product APIs.

## Intentional Simplifications

- No frontend or operator console.
- No external payment or shipping provider integration.
- No distributed tracing implementation.
- No full Kafka DLQ browser.
- No service extraction yet.
- Business scope is intentionally narrow so the reliability and workflow design remain the main signal.
