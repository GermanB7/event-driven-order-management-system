# ADR 0002: Orchestrated Saga + Outbox Reliability

- Status: Accepted
- Date: 2026-03-30

## Context
The core workflow spans order, inventory, payment, and shipping. We need eventual consistency with explicit failure handling and compensation.

## Decision
1. Use orchestrated saga in `workflow` as the primary coordination approach.
2. Publish integration events using transactional outbox.
3. Consumers are idempotent and prepared for retries and DLQ flows.
4. Every event carries `workflowId`, `correlationId`, and `causationId` for traceability.

## Consequences
- Clear flow ownership and deterministic compensations.
- Slightly higher complexity than in-process sync calls, but superior operational signal.
- Requires outbox processor and consumer idempotency stores in upcoming sprints.

