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
  execution/                    UC-4: ritual, monitoring and incident response
  pollen/                       One PollenService: reservations, consumption, audit and warnings
  completion/                   UC-5: reports, completion and archival
  port/                         Inbound-context/outbound dependency contracts
  shared/                       Audit application service, validation and exceptions
  UseCaseSupport.java           Shared command lookup/copy helpers
service/SeasonPlanService.java  Compatibility facade for existing callers
api/                            Existing REST adapter (package retained)
persistence/                    Existing JDBC adapters (package retained)
adapter/fake/                   Explicit educational integration fakes
adapter/pollentree/             Proposed external Pollen Tree HTTPS adapter
config/                         Existing Spring composition/security configuration
```

`com.seasonplan.security` remains the existing authentication/administration
boundary. It was not redesigned.

Dependency direction is **adapters/configuration → application → domain**.
Application ports may reference domain types, never concrete adapters. The domain
uses only the JDK. Architecture tests protect these boundaries without adding a
new framework or Maven module.

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
Other UC-4/UC-5 services also remain core-only: no new service composition,
REST routes, database adapters/migrations, sensors, schedulers or report renderers.

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
- All application pollen access follows **use case → `PollenService` →
  `PollenTreeClient` → `RestPollenTreeClient`** (or explicit dev/test fake).
  Approval, readiness, completion and the compatibility facade never use the
  external client directly. `PollenService` owns our read/command API and business
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
`PostgresLifecyclePersistenceTest`. On this host's Docker 29, Testcontainers 1.20.4
needs the following test-process-only API compatibility override:

```powershell
$env:DOCKER_API_VERSION = '1.44'
mvn.cmd -f .\backend\pom.xml '-Dapi.version=1.44' clean verify
```

To run the remaining tests explicitly when Docker is unavailable
(not a substitute for the PostgreSQL suite):

```powershell
mvn.cmd -f .\backend\pom.xml '-Dtest=!PostgresLifecyclePersistenceTest' clean verify
```

Execution/completion unit-test port fakes live under `src/test`. The replaceable
`adapter/fake/FakePollenTreeClient` is also available to explicitly configured dev/test
runtimes, never production. Mock transport and unit tests do not establish live
provider compatibility, database rollback or notification delivery; PostgreSQL
audit-retry and no-local-reservation-write tests require Docker. CI selects Temurin 17 and runs the full suite.
