# SeasonPlan backend

Java 17 / Spring Boot 3.2 / Maven. The application core is organized by the five
use cases in [`USE_CASE_SPECIFICATION.md`](../arch_docs/USE_CASE_SPECIFICATION.md).
See [`CHANGELOG.md`](CHANGELOG.md) for the implementation inventory, preserved
behavior, test results, and remaining integration work.

## Package layout

Under `src/main/java/com/seasonplan/lifecycle`:

```text
domain/                         Entities, immutable snapshots and domain rules
application/
  planning/                     UC-1: planning
  approval/                     UC-2: approval
  preparation/                  UC-3: preparation and readiness
  execution/                    UC-4: ritual, monitoring, incident response, consumption
  completion/                   UC-5: reports, completion and archival
  port/                         Inbound-context/outbound dependency contracts
  shared/                       Audit application service, validation and exceptions
  UseCaseSupport.java           Shared command lookup/copy helpers
service/SeasonPlanService.java  Compatibility facade for existing callers
api/                            Existing REST adapter (package retained)
persistence/                    Existing JDBC adapters (package retained)
adapter/fake/                   Existing temporary adapters
config/                         Existing Spring composition/security configuration
```

`com.seasonplan.security` remains the existing authentication/administration
boundary. It was not redesigned.

Dependency direction is **adapters/configuration → application → domain**.
Application ports may reference domain types, never concrete adapters. The domain
uses only the JDK. Architecture tests protect these boundaries without adding a
new framework or Maven module.

## Current runtime versus new core

The existing REST routes still use the compatibility facade and existing adapters.
UC-4 and UC-5 have executable, unit-tested Java application services, but **no new
REST routes, Spring beans, database adapters/migrations, sensor clients, schedulers,
notification delivery, or HTML/PDF renderers**. This is intentional.

To integrate those services later, implement the contracts in `application/port`
and compose them outside the core. In particular:

- `CommandTransaction` must serialize commands for a plan and atomically commit
  participating repositories, audit appends and notification outbox inserts.
- `ExecutionPorts.LaunchReadiness` must return real blockers, confirmed participant
  snapshots, and configured required ritual events. Do not use a permissive default.
- Metric rules and the report catalog-version port must use the plan's pinned
  catalog version, not whichever catalog is currently newest.
- `CompletionPorts.Tasks` must project all **required** work and missing required
  categories; it must not mistake the legacy preparation status `READY` for
  completion.
- `PollenTree` is authoritative for actual consumption and summary snapshots.
  Consumption event IDs must be idempotent across external retries. An external
  consumption cannot be rolled back by a local database transaction.
- Repositories must enforce the uniqueness/immutability contracts in their Javadoc.
  Outbox delivery, retries and recipient resolution belong to adapters.
- Exporters render only approved report snapshots. The core supplies the format,
  versioned filename and content type; it does not fabricate PDF/HTML content.

The existing database schema is **not** a UC-4/UC-5 persistence adapter. Its
constraints do not yet support all new audit/report/ritual representations.
Do not wire the new services to it without adapter contract tests and migrations.

## Tests on Windows PowerShell

Select an installed **JDK 17** before invoking Maven; the host's default Java may
be newer. This changes only the current PowerShell process:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
mvn.cmd -version
mvn.cmd -f .\backend\pom.xml clean verify
```

The full suite requires a running Docker daemon for
`PostgresLifecyclePersistenceTest`. To run the remaining tests explicitly when
Docker is unavailable (not a substitute for the PostgreSQL suite):

```powershell
mvn.cmd -f .\backend\pom.xml '-Dtest=!PostgresLifecyclePersistenceTest' clean verify
```

New deterministic port fakes live only under `src/test`. They exercise the core,
not database rollback, external service reliability, or notification delivery.
The existing CI already selects Temurin 17 and runs the full Maven test suite.
