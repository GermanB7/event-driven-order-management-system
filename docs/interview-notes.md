# Interview Notes

## What Is This Project?

It is a focused backend portfolio project that demonstrates event-driven order workflow orchestration. The goal is not to build a complete commerce product; the goal is to show distributed backend thinking in a bounded domain.

The workflow covers order creation, inventory reservation, payment authorization, fulfillment preparation, and compensation when a failure occurs.

## Why Not Microservices?

The project uses a modular monolith because the goal is to show boundaries and event-driven design without adding deployment and network complexity. The modules are structured so that future extraction is possible, but the current implementation remains simple to run and review.

Key point: microservices are not the starting point; clear boundaries are.

## Why Use A Transactional Outbox?

The outbox protects against a common reliability problem: committing database state while losing the event that should notify the rest of the system.

Order creation writes both the order and the `ORDER_CREATED` outbox event in one transaction. A relay publishes pending events to Kafka afterward.

## How Does The Saga Work?

The `workflow` module owns cross-module orchestration. It listens to domain events and emits the next command-style event in the workflow.

Happy path:

```text
ORDER_CREATED -> INVENTORY_RESERVATION_REQUESTED -> INVENTORY_RESERVED
-> PAYMENT_AUTHORIZATION_REQUESTED -> PAYMENT_AUTHORIZED
-> ORDER_CONFIRMED -> SHIPMENT_PREPARATION_REQUESTED
-> SHIPMENT_PREPARATION_STARTED
```

Payment failure compensation:

```text
PAYMENT_FAILED -> INVENTORY_RELEASE_REQUESTED -> INVENTORY_RELEASED -> ORDER_CANCELLED
```

## Where Is Idempotency Handled?

Consumers track processed messages by consumer name and event id. If Kafka redelivers an event or an outbox event is replayed, the consumer can detect the duplicate and avoid applying the same state transition twice.

This is important because event-driven systems should assume at-least-once delivery.

## How Do Retry, DLQ, And Replay Work?

Kafka listeners use retry/backoff configuration and publish exhausted failures to module-specific DLQ topics. The outbox relay also tracks retry attempts and can mark events as dead-lettered after max retries.

Replay is exposed through an internal endpoint that can requeue eligible outbox events:

```http
POST /internal/outbox/events/{eventId}/replay
```

This is not a full DLQ operations platform. It is a focused backend mechanism for demonstrating controlled event replay.

## What Makes The System Inspectable?

The `observability` module exposes internal endpoints for:

- order workflow timeline
- aggregate workflow status
- event lookup
- failed async operations
- operational summary
- replay request

The timeline is sourced from outbox events, which makes it useful for explaining what happened during asynchronous processing.

## What Is Intentionally Out Of Scope?

- No frontend.
- No product catalog.
- No real payment provider.
- No real shipping provider.
- No full DLQ browser.
- No distributed tracing implementation.
- No production deployment packaging.

These exclusions keep the portfolio signal focused on backend workflow reliability.

## What Would Be Extracted First?

The strongest extraction candidates are:

- `inventory`, if stock reservation needed independent scaling or ownership.
- `payments`, if real provider integration and compliance concerns grew.
- `shipping`, if fulfillment integrations became operationally complex.

The `workflow`, `outbox`, and `messaging` modules would need careful redesign before extraction because they coordinate reliability and event flow.
