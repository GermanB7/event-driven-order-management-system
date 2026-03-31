# Sprint 1 Foundation Freeze

## Scope
This sprint establishes architecture and infrastructure foundations only.

## Module Map
- `orders`: order aggregate and lifecycle state.
- `inventory`: reservation lifecycle.
- `payments`: authorization/compensation lifecycle.
- `shipping`: shipment preparation lifecycle.
- `workflow`: saga orchestration rules.
- `messaging`: event envelope/taxonomy + broker adapters.
- `outbox`: reliable publication model and processor.
- `observability`: correlation conventions, metrics, tracing hooks.
- `shared`: minimal primitives used by multiple modules.

## Frozen Decisions
- Modular monolith.
- Orchestrated saga.
- Transactional outbox publication.
- Idempotent consumers + retry/DLQ strategy.

## Exit Criteria
- Package skeleton created.
- ArchUnit boundaries running in CI.
- Docker Compose with PostgreSQL + Kafka.
- Flyway baseline migration in place.

