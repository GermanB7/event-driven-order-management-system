# Demo Runbook

This runbook shows how to run the project locally and demonstrate the main workflow paths.

## Start Infrastructure

```powershell
docker compose up -d
```

This starts PostgreSQL and Kafka.

## Run The Application

```powershell
.\mvnw.cmd spring-boot:run
```

Application URL:

```text
http://localhost:8080
```

Useful endpoints:

```text
http://localhost:8080/actuator/health
http://localhost:8080/swagger-ui/index.html
```

## Seed Inventory

The order workflow uses `SKU-1` in the demo request. Seed inventory before creating an order:

```powershell
docker exec -i edoms-postgres psql -U order_user -d order_management -c "insert into inventory.inventory_items (sku, available_quantity, reserved_quantity, updated_at) values ('SKU-1', 10, 0, now()) on conflict (sku) do update set available_quantity = excluded.available_quantity, reserved_quantity = excluded.reserved_quantity, updated_at = now();"
```

## Create An Order

```powershell
curl.exe -i -X POST http://localhost:8080/api/orders `
  -H "Content-Type: application/json" `
  -d "{\"customerId\":\"11111111-1111-1111-1111-111111111111\",\"currency\":\"USD\",\"items\":[{\"sku\":\"SKU-1\",\"quantity\":2,\"unitPrice\":10.00}]}"
```

Copy the returned `orderId` into the inspection commands below.

## Inspect Public Order State

```powershell
curl.exe http://localhost:8080/api/orders/{orderId}
```

## Inspect Workflow Timeline

```powershell
curl.exe http://localhost:8080/internal/orders/{orderId}/workflow
```

This shows outbox-backed workflow events, including event ids, event types, workflow metadata, publish status, retry counts, and timestamps.

## Inspect Aggregate Workflow State

```powershell
curl.exe http://localhost:8080/internal/orders/{orderId}/status
```

This summarizes order, inventory, payment, shipment, and event state for one order.

## Inspect Operational Summary

```powershell
curl.exe http://localhost:8080/internal/summary
```

## Inspect Failures

```powershell
curl.exe http://localhost:8080/internal/failures
```

## Request Outbox Replay

```powershell
curl.exe -i -X POST "http://localhost:8080/internal/outbox/events/{eventId}/replay?requestedBy=demo"
```

Replay only applies to eligible outbox events.

## Evidence Tests

Happy path:

```powershell
.\mvnw.cmd test -Dtest=FulfillmentWorkflowIntegrationTest
```

Payment failure compensation:

```powershell
.\mvnw.cmd test -Dtest=PaymentFailureCompensationIntegrationTest
```

Idempotency and replay safety:

```powershell
.\mvnw.cmd test -Dtest=IdempotencyE2EIntegrationTest
```

Main evidence suite:

```powershell
.\mvnw.cmd test -Dtest=FulfillmentWorkflowIntegrationTest,PaymentFailureCompensationIntegrationTest,IdempotencyE2EIntegrationTest
```

Architecture boundary test:

```powershell
.\mvnw.cmd test -Dtest=ModuleBoundariesArchTest
```
