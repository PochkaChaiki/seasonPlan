# SeasonPlan backend

Java 17 / Spring Boot 3.2 / Maven. The application core uses cohesive services
rather than one package/service per use case. See the
[service consolidation decision](../docs/decisions/0002-cohesive-lifecycle-services.md)
for merge choices, the [package ownership decision](../docs/decisions/0003-responsibility-owned-contracts-and-infrastructure.md)
for contract placement, the [security boundary decision](../docs/decisions/0004-security-feature-and-lifecycle-authorization.md)
for identity and authorization ownership, the [execution implementation guide](../docs/architecture/EXECUTION_RULES_AND_PERSISTENCE.md)
for required configuration and legacy-data rollout, and [`CHANGELOG.md`](CHANGELOG.md)
for verification and remaining integration work.

## Package layout

Under `src/main/java/com/seasonplan/lifecycle`:

```text
domain/                         Entities, immutable snapshots and domain rules
service/
  SeasonPlanService.java        Drafting, validation, review, checklist and ritual readiness
  MagicTaskService.java         Task preparation, fairy assignments and schedule checks
  PollenService.java            Sole pollen reservation/consumption integration boundary
  RitualService.java            Ritual attempts/events and successful plan activation
  MonitoringService.java        Measurements, deviation responses and critical warnings
  IncidentResponseService.java  Incidents, corrective actions and overdue escalation
  SeasonReportService.java      Report snapshots, approval/completion, export and archival
  AuditLogService.java          Immutable audit append/read operations
  ServiceSupport.java           Package-private command lookup/copy helpers
  ValidationSupport.java        Package-private field validation helpers
  ...                           Service-owned transaction, rule and report contracts
authorization/                  Shared operation permission matrix and resource-access policy
repository/                     Framework-independent persistence/query interfaces
integration/pollen/             Pollen Tree client contract and availability failure
integration/calendar/           Natural Calendar client contract
notification/                   Outbox submission, delivery and recipient contracts
exception/                      API-neutral lookup failures
api/                            REST controllers and API models
infrastructure/jdbc/            JDBC repositories, transactions, outbox/inbox and delivery worker
infrastructure/http/            External Pollen Tree HTTPS implementation
infrastructure/mail/            SMTP implementation and configured address resolver
infrastructure/fake/            Explicit educational integration fakes
config/                         Lifecycle/integration/notification Spring composition
```

Security remains a sibling feature rather than being merged wholesale into
lifecycle. Under `src/main/java/com/seasonplan/security`:

```text
identity/                       Public framework-neutral ActorRole, CurrentActor and provider
api/                            Auth/admin controllers and account/access error mapping
domain/                         Managed account values and username rules
service/                        Core account service, validated commands, hash/transaction contracts
repository/                     UserAccountRepository persistence contract
exception/                      Neutral account and denied-access failures
infrastructure/jdbc/            Account SQL and database-backed authentication lookup
infrastructure/spring/          Principal/context, auth handlers, password and transaction adapters
config/                         Filter chain, account composition and dev-only bootstrap
```

Security has no dependency on lifecycle. Lifecycle-specific permissions belong to
`lifecycle/authorization`, not to the authentication filter chain. Lifecycle API
role selection and service guards use that same policy. Ownership checks execute
at the service boundary; business transitions still belong to domain/application
logic. An administrator flag is separate from the seven business roles and never
grants implicit lifecycle permissions.

Account commands receive the trusted current actor, not a Spring principal or a
user-selected target UUID. The core uses repository/password/transaction contracts;
credential changes lock and verify the current stored hash before atomic updates.
The controller invalidates its session only after success. Existing lifecycle
role/actor-ID signatures remain trusted internal application contracts; the API
binds both to its authenticated actor, not request-body/header claims.

**Deployment requires a one-time sign-in for old sessions** because serialized
principal/role class names moved. Follow the coordinated, operator-controlled
[session rollout procedure](../docs/architecture/ST-01-authentication-and-rbac.md#security-package-refactor-rollout).
No session/account data is deleted automatically.

Dependency direction is **API/configuration → services → domain/contracts**;
infrastructure implements repository, integration and notification contracts.
Services and contracts are framework-independent; neither imports concrete
infrastructure. Domain code uses the JDK and the public neutral identity role
catalog; it does not depend on service, policy or infrastructure implementations. Architecture tests protect these boundaries without a new framework or
Maven module.

Interfaces live with their responsibility, not in a global interface directory.
For example, `repository/SeasonPlanRepository` is implemented by
`infrastructure/jdbc/JdbcSeasonPlanRepository`; `integration/pollen/PollenTreeClient`
is implemented by `infrastructure/http/RestPollenTreeClient`.
`CommandTransaction`, `RitualReadinessChecker`,
`MetricRuleProvider`, `IncidentRuleProvider`, `RequiredTaskResults`,
`CatalogVersionProvider` and `SeasonReportExporter` are service-owned contracts in
`service`. The related `Readiness` and `MetricRule` records remain nested in their
owning interfaces. There are no `application`, `port`, `shared`, `adapter` or
top-level `persistence` source packages.

## Current runtime versus new core

Review uses `POST /season-plans/{id}/verify` (Chief Guardian) before approval.
Verify, approve and return save the plan, audit entry and notification event in a
single JDBC command transaction. A scheduled worker delivers committed events
independently per recipient/channel. IN_APP writes an idempotent durable inbox row.
EMAIL uses configurable SMTP and otherwise fails closed for retry. Delivery uses
exponential retry until 24 hours after the event, then records EXPIRED. Approval accepts immutable typed conditions. Until a verified-evidence
implementation exists, every condition is an unmet readiness blocker; manually
completing a checklist item cannot satisfy it. Review history is readable by the
Chief Guardian or the plan creator with the Fairy Planner role; other roles are
denied until a source-backed visibility policy exists. Review validation rechecks
the calendar and overlapping applicable plans by normalized territory and window.

### Review notification delivery

`NotificationDeliveryGateway` is replaceable. The default `JdbcNotificationDeliveryGateway`
writes IN_APP to `notification_inbox` (one row per event id and recipient).
Authenticated `GET /notifications` reads only the current server-session user's
inbox, at most 50 entries ordered newest first, with event ID as a stable tie-breaker.
The Dashboard displays these messages and provides a refresh control; no mark-read
workflow is implemented. EMAIL uses `SmtpNotificationEmailGateway`, with mandatory
STARTTLS (TLS 1.2/1.3), hostname verification and 3-second connect/read/write timeouts. SMTP is
not live-verified. Without complete configuration or a valid recipient mapping,
EMAIL fails closed and is retried; it is never marked delivered by a fake.
SMTP itself cannot guarantee exactly-once delivery if a process fails after send.
The gateway includes `X-SeasonPlan-Event-ID`; the downstream provider/consumer must
deduplicate that event id per recipient. The temporary configured UUID-to-email
resolver is replaceable by an identity directory; it does not derive an address
from a username. Use secrets management for credentials and only deployment-owned
addresses. For example, an application configuration may contain:

```yaml
notifications:
  smtp:
    host: smtp.example.invalid
    port: 587
    username: ${SMTP_USERNAME}
    password: ${SMTP_PASSWORD}
    from: sender@example.invalid
    recipients:
      "11111111-1111-1111-1111-111111111111": planner@example.invalid
```

The worker starts after 60 seconds and polls every 30 seconds by default; override
`notifications.initial-delay-ms` and `notifications.poll-delay-ms` as needed.
Per-channel delivery state prevents a failed email from blocking IN_APP. The
outbox is committed before the separate worker runs; a delivery failure cannot
roll back the review decision.

Existing reservation REST routes use the unified `PollenService`. Its consumption
logic remains executable and unit-tested but has no consumption REST route or
complete audit/outbox persistence/delivery slice. Its separate consumption outbox
is deliberately unconfigured, so it fails before external spending.
Ritual, monitoring and incident response now have real runtime composition:

- `PinnedMetricRuleProvider` resolves plan targets and pinned metric settings.
- `ConfiguredIncidentRuleProvider` resolves the explicitly selected incident catalog.
- `ComputedRitualReadinessChecker` checks actual tasks, confirmed assignments,
  required roles, authoritative pollen, manual flags and critical incidents.
- `JdbcMagicIncidentRepository`, `JdbcCorrectionActionRepository`,
  `JdbcRitualSessionRepository` and `JdbcClimateMetricRepository` persist their
  domain state through forward migrations 012/013 and PostgreSQL constraints.

These are wired into their consuming services, not just supplied by test lambdas.
**Configure versioned execution and incident rules before creating new plans.**
No business defaults are supplied. New plans pin an immutable execution snapshot
atomically with creation/audit. All three metric targets must be supplied on the
plan. Existing incomplete metadata is preserved and fails closed, not guessed.
See the [configuration and safe rollout guide](../docs/architecture/EXECUTION_RULES_AND_PERSISTENCE.md)
and [ADR 0005](../docs/decisions/0005-configured-execution-and-durable-repositories.md).

The existing task API accepts additive `category` metadata. Complete compatible,
timestamped confirmed assignments before completing their task; READY is not
COMPLETED. Readiness and ritual execution share the checker. Per-plan transactions
also protect task updates/audit and assignments; permitted ongoing task work
remains available after readiness/activation, including during critical incidents.

No new execution REST routes, UI, sensors or escalation scheduler are included.
Reporting still remains core-only. Remaining integration constraints include:

- `CommandTransaction` serializes plan commands and atomically commits state,
  repositories, audit and outbox; its creation operation does not lock a nonexistent row.
- Approval-condition evidence is still unverified and blocks readiness.
- Metric rules use the persisted snapshot; future report catalog projection must
  use the same plan selection rather than whichever catalog is currently newest.
- `RequiredTaskResults` must project all **required** work and missing required
  categories; it must not mistake the legacy preparation status `READY` for
  completion.
- All application pollen access follows **application service → `PollenService` →
  `PollenTreeClient` → `RestPollenTreeClient`** (or explicit dev/test fake).
  `SeasonPlanService` and `SeasonReportService` never use the external client directly. `PollenService` owns our read/command API and business
  orchestration; `RestPollenTreeClient` is only the replaceable HTTPS adapter.
  Public facade results are client-independent domain values. Architecture tests
  protect this boundary. There is no local reservation repository or projection persistence. Only our
  immutable audit and report data remain local; the old migration's unused table
  is retained without writes to avoid silently deleting historical data.
  Request, decision and consumption replays must be idempotent at the provider;
  audit/outbox effects are deduplicated locally. External changes cannot be
  rolled back by our database. See the [Pollen integration handoff](../docs/architecture/POLLEN_TREE_EXTERNAL_SERVICE.md)
  for Vision/SRS requirements, the simplified design, proposed contract and limits.
- Repositories must enforce the uniqueness/immutability contracts in their Javadoc.
  Outbox delivery, retries and recipient resolution belong to adapters.
- Exporters render only approved report snapshots. The core supplies the format,
  versioned filename and content type; it does not fabricate PDF/HTML content.

The schema now includes the requested execution persistence and extended audit
actions. The legacy `season_reports` table is still **not** a compatible adapter
for the new approved-report snapshots; reporting needs its own forward migration,
implementation and integration tests. Role-labelled notifications also need
appropriate identity-recipient expansion before individual inbox receipt can be
claimed; writing an outbox event is not proof of delivery.

## Tests on Windows PowerShell

Select an installed **JDK 17** before invoking Maven; the host's default Java may
be newer. This changes only the current PowerShell process:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
mvn.cmd -version
mvn.cmd -f .\backend\pom.xml clean verify
```

The full suite requires a running Docker daemon for
`PostgresLifecyclePersistenceTest`, `PostgresAccountAdministrationTest` and
`PostgresExecutionRepositoriesTest`. On this host's Docker 29, Testcontainers 1.20.4
needs the following test-process-only API compatibility override:

```powershell
$env:DOCKER_API_VERSION = '1.44'
mvn.cmd -f .\backend\pom.xml '-Dapi.version=1.44' clean verify
```

To run the remaining tests explicitly when Docker is unavailable
(not a substitute for the PostgreSQL suite):

```powershell
mvn.cmd -f .\backend\pom.xml '-Dtest=!Postgres*Test' clean verify
```

Service unit tests and deterministic execution/report port fakes live under
`src/test/java/com/seasonplan/lifecycle/service`. The replaceable
`infrastructure/fake/FakePollenTreeClient` is also available to explicitly configured
dev/test runtimes, never production. `AllowingNaturalCalendarClient` remains the
existing permissive educational stub, not a verified calendar integration or proof
of launch readiness. Mock transport and unit tests do not establish live
provider compatibility, database rollback or notification delivery; PostgreSQL
audit-retry and no-local-reservation-write tests require Docker. CI selects Temurin 17 and runs the full suite.
