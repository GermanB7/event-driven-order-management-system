# Event-Driven Order Management System

Modular monolith backend portfolio project focused on event-driven workflow design, eventual consistency, saga orchestration, and operational reliability.

## Sprint 1 Foundation
- Architecture decisions in `docs/adr`.
- Module skeleton and architecture tests.
- PostgreSQL + Kafka local stack via Docker Compose.
- Flyway baseline migration.
- Event envelope and initial taxonomy.

## Run locally
```powershell
# Start infrastructure
docker compose up -d

# Run tests
.\mvnw.cmd test

# Run application
.\mvnw.cmd spring-boot:run
```

## Main packages
`orders`, `inventory`, `payments`, `shipping`, `workflow`, `messaging`, `outbox`, `observability`, `shared`


