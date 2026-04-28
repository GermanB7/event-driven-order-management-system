# Sprint 8: Operational Visibility & Internal Inspection

## Summary

Sprint 8 implements operational visibility and internal inspection capabilities for the event-driven async workflow. This enables operators to understand what happened to any order, track async operations, diagnose failures, and inspect current system state.

## Implemented Features

### 1. Order Workflow Timeline Inspection
- **Endpoint**: `GET /internal/orders/{orderId}/workflow`
- **Response**: `OrderWorkflowInspectionView`
  - Timeline of all events emitted for the order (sourced from outbox)
  - Correlated metadata: `eventId`, `workflowId`, `correlationId`, `causationId`
  - Processing status: `PENDING`, `PUBLISHED`, `FAILED`
  - Retry counts and next retry timestamp
  - Current derived workflow state (last event type or `NO_EVENTS`)

### 2. Order Status Aggregate
- **Endpoint**: `GET /internal/orders/{orderId}/status`
- **Response**: `WorkflowStatusAggregateView`
  - Order status (created, confirmed, cancelled, fulfillment_requested)
  - Inventory reservation status
  - Payment status
  - Shipment status
  - Event counts: total, failed, dead-lettered

### 3. Failed Async Operations Inspection
- **Endpoint**: `GET /internal/failures`
- **Response**: List of `FailedAsyncOperationView`
  - All FAILED events pending retry
  - Consumer name, event type, retry count
  - Failure reason and retry schedule
  - Dead-letter status

### 4. Workflow Traceability by ID
- **Endpoint**: `GET /internal/workflows?workflowId={workflowId}`
  - Returns all events for a workflow (stub implementation for H2 compatibility)
- **Endpoint**: `GET /internal/events/{eventId}`
  - Returns specific event by eventId

### 5. Operational Summary & Metrics
- **Endpoint**: `GET /internal/summary`
- **Response**: `OperationalSummaryView`
  - Order counts by status
  - Pending and failed outbox event counts
  - Total events published
  - Dead-letter counts

## Architecture

### New Packages
- `observability.application`: Use cases
  - `GetOrderWorkflowInspectionUseCase`
  - `GetWorkflowStatusAggregateUseCase`
  - `ListFailedAsyncOperationsUseCase`
  - `FindEventsByWorkflowIdUseCase`
  - `FindEventsByEventIdUseCase`
  - `GetOperationalSummaryUseCase`
- `observability.application.port`: Repository interfaces
  - `WorkflowTimelineRepository` (timeline read model)
  - `OperationalInspectionRepository` (aggregates & queries)
- `observability.application.dto`: Response DTOs
  - `OrderWorkflowInspectionView`
  - `WorkflowStatusAggregateView`
  - `OperationalSummaryView`
  - `FailedAsyncOperationView`
  - `WorkflowTimelineEventView`
- `observability.infrastructure.persistence`: Data access
  - `JdbcWorkflowTimelineRepository` (JDBC-based timeline)
  - `JdbcOperationalInspectionRepository` (JDBC-based queries)
- `observability.interfaces.http`: REST endpoints
  - `InternalWorkflowInspectionController` (`/internal/*`)

### Database

#### New Indexes (V7 Migration)
```sql
create index idx_outbox_event_aggregate_occurred_id
    on outbox.outbox_event (aggregate_id, occurred_at, id);

create index idx_outbox_event_status_next_retry
    on outbox.outbox_event (status, next_retry_at desc nulls last)
    where status = 'FAILED';

create index idx_outbox_event_headers_workflow_id
    on outbox.outbox_event using GIN ((headers -> 'workflowId'))
    where headers ? 'workflowId';  /* PostgreSQL only */
```

#### Data Sourcing
- Timeline: queries `outbox.outbox_event` directly (no materialized view needed)
- Failures: filters `outbox_event` by `status = 'FAILED'`
- Status aggregates: joins domain tables (`orders`, `inventory_reservations`, `payments`, `shipments`)
- Metadata: extracts from `headers` JSON (eventId, workflowId, correlationId, causationId)

## Design Decisions

1. **Pragmatic Internal Read Model**
   - No event sourcing redesign
   - Outbox is the source of truth for timeline
   - Direct SQL queries for operational summaries
   - No cache layer (queries are fast enough)

2. **Strict Module Boundaries**
   - Observability can depend on Orders/Inventory/Payments/Shipping (read-only)
   - All consumers of observability are internal (`/internal` prefix)
   - No external APIs expose this functionality

3. **H2 vs PostgreSQL SQL**
   - Timeline queries work on both (aggregate_id filtering)
   - JSON-based workflowId search deferred (PostgreSQL-only indexes)
   - Failure inspection fully compatible

4. **No New Business State**
   - Timeline sourced entirely from outbox events
   - Status aggregates computed on-demand
   - No mutation endpoints

## Testing

### Unit Tests
- `GetOrderWorkflowInspectionUseCaseTest`: timeline ordering, state derivation

### Integration Tests
- `InternalWorkflowInspectionIntegrationTest`: endpoint, H2 schema
- `InternalOperationalEndpointsIntegrationTest`: failures/summary endpoints

### Coverage
- Order workflow timeline (happy path + no events)
- Failures inspection
- Operational summary metrics

## Observability Chain

### Request Flow Example
1. **Operator**: `GET /internal/orders/{orderId}/workflow`
2. **Controller**: routes to `GetOrderWorkflowInspectionUseCase.execute(orderId)`
3. **Use Case**: calls `WorkflowTimelineRepository.findByOrderId(orderId)`
4. **JDBC Repo**: queries `outbox_event` where `aggregate_id = ?`, parses headers
5. **Response**: 
   ```json
   {
     "orderId": "...",
     "workflowId": "wf-123",
     "currentWorkflowState": "PAYMENT_AUTHORIZED",
     "timeline": [
       {
         "eventId": "...",
         "eventType": "ORDER_CREATED",
         "workflowId": "wf-123",
         "correlationId": "...",
         "publishStatus": "PUBLISHED",
         "occurredAt": "2026-04-04T...",
         "retryCount": 0
       },
       ...
     ]
   }
   ```

## Known Limitations

1. **JSON-based workflowId Search**
   - Not tested against H2 (uses stub)
   - PostgreSQL: use `headers ->> 'workflowId'`
   - H2: deferred (can use `JSON_EXTRACT` or denormalize)

2. **Dead-Letter Counts**
   - Current implementation: hardcoded 0
   - Future: integrate with Kafka consumer groups or dedicated DLQ table

3. **Replay Support**
   - Not exposed in this sprint
   - Can be added later to replay failed events

## Next Steps (Sprint 9+)

- [ ] Dead-letter queue integration (Kafka consumer group offset tracking)
- [ ] Replay endpoint: `POST /internal/failures/{failureId}/replay`
- [ ] Enhanced failure classification (error codes, root causes)
- [ ] Metrics export (Prometheus gauges for pending/failed events)
- [ ] Audit logging of inspection queries
- [ ] Time-series archival of timeline events

## Files Changed

### New Files (11)
- `observability/application/GetOrderWorkflowInspectionUseCase.java`
- `observability/application/FindEventsByWorkflowIdUseCase.java`
- `observability/application/FindEventsByEventIdUseCase.java`
- `observability/application/ListFailedAsyncOperationsUseCase.java`
- `observability/application/GetWorkflowStatusAggregateUseCase.java`
- `observability/application/GetOperationalSummaryUseCase.java`
- `observability/application/dto/OrderWorkflowInspectionView.java`
- `observability/application/dto/WorkflowStatusAggregateView.java`
- `observability/application/dto/OperationalSummaryView.java`
- `observability/application/dto/FailedAsyncOperationView.java`
- `observability/application/port/OperationalInspectionRepository.java`

### Modified Files (2)
- `observability/application/port/WorkflowTimelineRepository.java` (created in Sprint 8 increment 1)
- `observability/infrastructure/persistence/JdbcWorkflowTimelineRepository.java` (created in Sprint 8 increment 1)
- `observability/infrastructure/persistence/JdbcOperationalInspectionRepository.java` (new)
- `observability/interfaces/http/InternalWorkflowInspectionController.java` (expanded from 1 endpoint to 6)
- `src/main/resources/db/migration/V7__outbox_workflow_inspection_indexes.sql` (expanded)

### Test Files (2)
- `observability/application/GetOrderWorkflowInspectionUseCaseTest.java`
- `observability/integration/InternalWorkflowInspectionIntegrationTest.java`
- `observability/integration/InternalOperationalEndpointsIntegrationTest.java`

## Compliance

✅ Does NOT add business features (returns/notifications/delivery)
✅ Does NOT mutate state (all read-only)
✅ Does NOT break module boundaries
✅ Does NOT change existing business logic
✅ Enhances observability & debuggability
✅ Pragmatic (no full event sourcing redesign)
✅ Interview-explainable (single SQL source, clear DTO responses)

