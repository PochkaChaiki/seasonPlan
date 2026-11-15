# Responsibility-owned contracts and infrastructure packages

Status: Accepted. Supplements ADR 0002; supersedes its retained `application/port`,
`application/shared`, `adapter` and `persistence` package locations, not its service
ownership decisions. [ADR 0004](0004-security-feature-and-lifecycle-authorization.md)
subsequently moves shared identity/denied-access contracts into security and
introduces lifecycle-owned authorization; the remaining package decisions stand.

## Context

Moving the lifecycle services out of use-case packages left `application` holding
only interfaces and shared helpers. That mixed two valid organizational styles
without providing a useful module boundary. The port bucket combined repositories,
external clients, transaction/context contracts, report projections and delivery
extensions. The shared bucket combined service-local validation, lookup/access
errors and a Pollen Tree integration failure.

The interface contracts are necessary for deterministic tests, dependency inversion
and replaceable integrations. Their common Java type is not a sufficient reason
to put all of them in one package. The project does not need a global `interfaces`,
`impl`, `common` or `shared` directory, or an interface for every business service.

## Decision

Keep the existing `lifecycle` and `security` feature boundaries. Within lifecycle,
place contracts with the responsibility they describe and concrete implementations
under `infrastructure`:

| Package | Responsibility |
| --- | --- |
| `api` | REST controllers, wire models and HTTP exception mapping |
| `domain` | Entities, value objects, invariants and domain validation failures |
| `service` | Eight cohesive application services, service-owned extension contracts and package-private helpers |
| `repository` | Framework-independent persistence/query contracts |
| `integration.pollen` | Pollen Tree client contract and authoritative-provider availability failure |
| `integration.calendar` | Natural Calendar client contract |
| `notification` | Transactional notification submission and delivery/recipient contracts |
| `exception` | API-neutral lookup and application authorization failures |
| `infrastructure.jdbc` | JDBC repositories, transactions, outbox/inbox and durable delivery worker |
| `infrastructure.http` | External HTTPS/JSON integration implementation |
| `infrastructure.mail` | SMTP implementation and configured recipient-address resolver |
| `infrastructure.fake` | Existing deterministic educational/dev/test adapters |
| `config` | Spring composition, settings and security wiring |

There is no remaining `application`, `port`, `shared`, `adapter` or top-level
`persistence` source package in lifecycle. This is a physical package refactor,
not a new module framework or a change to lifecycle business ownership.

## Contract mapping

- Existing plan, task, assignment, audit and notification-inbox repository
  interfaces move to `repository`.
- `ExecutionPorts.Rituals`, `.Metrics`, `.Incidents` and `.Corrections` become
  `RitualSessionRepository`, `ClimateMetricRepository`, `MagicIncidentRepository`
  and `CorrectionActionRepository` in `repository`.
- `CompletionPorts.Reports` becomes `repository.SeasonReportRepository`.
- `ExecutionPorts.LaunchReadiness`, `.MetricRules` and `.IncidentRules` become
  `service.RitualReadinessChecker`, `MetricRuleProvider` and `IncidentRuleProvider`.
  `Readiness` and `MetricRule` remain nested in their respective owning interfaces;
  their constructor validation and defensive copies are unchanged.
- `CompletionPorts.Tasks`, `.CatalogVersion` and `.Exporter` become
  `service.RequiredTaskResults`, `CatalogVersionProvider` and `SeasonReportExporter`.
- The grouped `ExecutionPorts` and `CompletionPorts` namespace classes are removed.
  Interface method names and semantics remain unchanged.
- At adoption, `CommandTransaction` and `CurrentActorProvider` moved to `service`
  as framework-independent contracts. ADR 0004 subsequently moves actor lookup
  and identity types into `security.identity`; command transactions stay service-owned.
- `PollenTreeClient` and `PollenTreeUnavailableException` move to
  `integration.pollen`; `NaturalCalendarService` becomes
  `integration.calendar.NaturalCalendarClient`. Its permissive educational fake
  is renamed `AllowingNaturalCalendarClient` and remains explicitly fake.
- `NotificationOutbox`, `NotificationDeliveryGateway` and
  `NotificationRecipientResolver` move to `notification`. Delivery/recipient
  interfaces are owned by notification processing, not mislabeled as business
  use cases.
- `ValidationSupport` moves into `service` and becomes package-private.
  `NotFoundException` and `AccessDeniedForRoleException` move to `exception`;
  `DomainValidationException` stays in `domain`. Exceptions contain no HTTP status
  or framework dependencies; the API adapter maps them to HTTP responses.

## Dependency rules

API code invokes services/contracts. Services depend on domain values and inward,
framework-independent repository, integration and notification contracts.
Infrastructure implements those contracts, and configuration wires both sides.
Neither domain nor service code depends on concrete infrastructure, Spring, JDBC,
Jackson or transport clients. Repository contracts do not depend on business
service implementations. Only `PollenService` may use the Pollen Tree client in
business orchestration; configuration and infrastructure may compose/implement it.

An interface and implementation can share a Java package when that is cohesive.
Here separate infrastructure packages are useful because JDBC, HTTP and SMTP
introduce dependencies that the business core must not inherit. Package names do
not enforce these rules by themselves; architecture tests check the imports and
ownership boundaries.

## Compatibility and limits

HTTP routes/payloads, roles, lifecycle states, domain entities, SQL/migrations,
provider requests, audit history and transaction/outbox behavior are unchanged.
Removed Java package/type names are intentionally not retained as compatibility
wrappers. Internal callers, Spring wiring and tests migrate to the new owners.
The split preserves append-only audit/report contracts, import/replay uniqueness,
per-plan command serialization and all readiness/rule validation.

No production repositories, sensor connections, exporters, scheduling behavior or
new mock integrations are introduced. Existing deterministic fakes and fail-closed
runtime gaps remain documented in `backend/README.md`. Existing native UML artifacts
are historical snapshots and are not regenerated by this package migration.

## Verification

Architecture tests are run red before production moves, then green against the
new contract locations, absence of obsolete namespaces, framework-free core and
exclusive pollen access. Existing behavioral, REST/security, composition and
persistence tests are migrated rather than replaced. Focused contract tests cover
the moved records' immutability and invalid-configuration rejection.

Builds/tests use JDK 17. PostgreSQL rollback/concurrency validation still requires
a working Docker environment; a passing non-Docker suite is not a substitute.
Current commands and actual results are recorded in `backend/CHANGELOG.md`.
