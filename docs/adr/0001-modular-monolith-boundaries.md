# ADR 0001: Modular Monolith with Strict Boundaries

- Status: Accepted
- Date: 2026-03-30

## Context
Sprint 1 requires a monolith that demonstrates event-driven workflows without premature microservice split. We need strong module boundaries to prevent a shared-ball-of-mud.

## Decision
Adopt a modular monolith organized by business module packages:

- `orders`
- `inventory`
- `payments`
- `shipping`
- `workflow`
- `messaging`
- `outbox`
- `observability`
- `shared` (minimal cross-cutting primitives only)

Boundary rules:

1. Module internals are private by package convention.
2. Cross-module collaboration goes through explicit ports/events, not direct internals.
3. `shared` cannot contain business logic.
4. `orders` must not directly orchestrate `inventory`/`payments` internals; `workflow` does orchestration.

## Consequences
- Better maintainability and future extraction path.
- Slightly more upfront discipline and test enforcement.
- Architecture tests become a mandatory safety net.

