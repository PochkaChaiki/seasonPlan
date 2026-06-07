# Persistence Layer Implementation Report

Date: 2026-06-07

## Summary

The lifecycle application was changed from production in-memory storage to PostgreSQL-backed persistence.

The service layer now depends on repository interfaces. Spring production wiring uses JDBC repositories that read and write the PostgreSQL schema from Flyway migrations. The existing in-memory repositories remain as deterministic test doubles for unit tests.

## Main Changes

### Repository Abstractions

Added repository interfaces under `backend/src/main/java/com/seasonplan/lifecycle/service`:

* `SeasonPlanRepository`
* `PollenReservationRepository`
* `MagicTaskRepository`
* `FairyAssignmentRepository`
* `AuditLogRepository`

Updated lifecycle services to depend on these interfaces instead of concrete in-memory classes.

### PostgreSQL JDBC Persistence

Added JDBC repository implementations under `backend/src/main/java/com/seasonplan/lifecycle/persistence`:

* `JdbcSeasonPlanRepository`
* `JdbcPollenReservationRepository`
* `JdbcMagicTaskRepository`
* `JdbcFairyAssignmentRepository`
* `JdbcAuditLogRepository`
* `JdbcJson`

Persisted data includes:

* Season plans
* Season plan target territories
* Season plan time windows
* Readiness checklist items
* Pollen reservations
* Magic tasks
* Fairy assignments
* Audit log entries

`SeasonPlan` structured fields are stored in PostgreSQL `jsonb` columns:

* `natural_phenomenon_parameters`
* `target_climate_metrics`

### Spring Configuration

Updated `LifecycleConfiguration` so application services are wired with repository interfaces.

Spring now discovers the JDBC repository implementations via component scanning and uses them for normal application runtime.

### In-Memory Test Doubles

Updated existing in-memory repositories to implement the new repository interfaces.

Added:

* `InMemoryAuditLogRepository`

This keeps fast domain/service tests isolated from PostgreSQL while production uses JDBC persistence.

### Tests

Added PostgreSQL integration coverage:

* `PostgresLifecyclePersistenceTest`

This test starts PostgreSQL through Testcontainers, applies Flyway migrations, executes lifecycle services, and verifies rows are written to the database.

Covered persistence paths:

* Draft creation
* Submit transition
* Audit log append
* Pollen reservation confirmation
* Magic task creation
* Fairy assignment creation
* Readiness checklist completion
* `RITUAL_READY` transition

## Verification

Ran:

```powershell
Set-Location X:\projects\seasonPlan\backend
mvn.cmd test
```

Result:

* 15 tests passed
* 0 failures
* 0 errors

## Operational Notes

Flyway remains responsible for schema creation.

For local Docker startup with migrations:

```powershell
Set-Location X:\projects\seasonPlan
docker compose up -d postgres flyway
```

After that, running the backend will use PostgreSQL through the configured datasource in `application.yml`.
