# Backend changelog

## Unreleased — Use-case-oriented clean architecture

### Scope and review summary

Reorganized the Java backend around the five use cases in
[`arch_docs/USE_CASE_SPECIFICATION.md`](../arch_docs/USE_CASE_SPECIFICATION.md).
Preserved the existing planning/approval/preparation behavior and added the
previously missing execution and completion **domain/application cores**.
No new production adapters were added.

Business semantics for the new cores follow the existing
[ST-00 specification resolution](../docs/architecture/ST-00-specification-resolution.md).
In particular, correction/emergency are calculated operating conditions, **not**
additional `SeasonPlanStatus` values. The eight existing lifecycle statuses and
their transition-table tests are unchanged.

### 1. Structural changes

| Previously | Now | Reason |
| --- | --- | --- |
| Planning methods inside `service/SeasonPlanService` | `application/planning/PlanningService` | UC-1 ownership |
| Review methods inside that same service | `application/approval/ApprovalService` | UC-2 ownership |
| Ritual-readiness transition inside that service | `application/preparation/PreparationService` | UC-3 ownership |
| Task, assignment, schedule, reservation and checklist services in `service/` | `application/preparation/` | Keep preparation responsibilities together |
| Repository, calendar and current-actor interfaces in `service/` | `application/port/` | Explicit dependency-inversion boundaries |
| Audit service, validation helpers and application exceptions in `service/` | `application/shared/` | Shared framework-independent application support |
| Calendar/notification temporary implementations in `service/` | `adapter/fake/` | Concrete integrations must stay outside the core |
| Approval directly dependent on the notification implementation | `PlannerNotifications` interface | Preserve behavior without coupling the use case to a fake |

`service/SeasonPlanService` remains a small delegating compatibility facade.
Existing REST callers and Spring wiring still use it. Moved types required import
updates in JDBC adapters, security integration, configuration and existing tests;
those components' behavior was not rewritten.

Existing `domain/`, `api/`, `persistence/`, `config/`, and authentication packages
were retained rather than renamed for cosmetic consistency. See
[`README.md`](README.md) for the complete dependency layout.

### 2. Use-case implementation inventory

| Use case | Application entry points | Work performed |
| --- | --- | --- |
| UC-1 — Планирование сезона | `PlanningService` | Extracted existing create/edit/validate/submit/read behavior |
| UC-2 — Согласовать сезонный план | `ApprovalService` | Extracted existing review/return behavior; isolated notification dependency |
| UC-3 — Подготовка сезона к запуску | `PreparationService`, existing preparation services | Relocated existing resource/task/assignment/checklist logic and extracted readiness transition |
| UC-4 — Выполнение и контроль сезона | `RitualService`, `MonitoringService`, `IncidentResponseService`, `PollenConsumptionService` | Added executable domain/application logic and outbound contracts |
| UC-5 — Завершение сезона | `CompletionService` | Added executable report/completion/archive logic and outbound contracts |

The first three rows are behavior-preserving extractions, not claims that every
requirement in the much larger SRS was already implemented.

### 3. New UC-4 behavior

- **Ritual sessions:** participant snapshots, numbered attempts, ordered events,
  structured failure details, one running/successful attempt, retry after failure,
  and command-time readiness revalidation. Missing completion events, a critical
  incident, or revoked/replaced participants prevent activation. Failed completion
  records a failed session and leaves the season `RITUAL_READY`.
- **Climate metrics:** catalog-checked units, positive tolerances, immutable
  measurements, four-decimal `abs(value - target) / tolerance` scores, manual or
  imported entries, ordered history, and import-key idempotency. Changed retry
  payloads are rejected. Rule versions, targets, owners and response deadlines
  come through a port rather than an invented infrastructure implementation.
- **Deviation response:** scores >= 1 create corrective work; scores >= 2 also
  create a critical climate incident and notification. Repeated imports do not
  duplicate those effects.
- **Incidents:** catalog severity, Chief Guardian overrides with a reason,
  registration/resolution/reopening, linked-action checks, notifications, and a
  calculated critical operating condition. Resolving the last critical incident
  clears the condition without changing the lifecycle status.
- **Corrective actions:** responsible owner, deadline, guarded state transitions,
  cancellation reasons, completion checks and callable overdue escalation. Outbox
  keys deduplicate a level within a deadline/day; a later overdue day produces a
  new level. No scheduler adapter was added.
- **Pollen consumption:** only through the new authoritative `PollenTree` port,
  grams rounded to scale 3, comparison against confirmed totals, and notifications
  at 80%, 100% and over 100%. The Chief Guardian is included for over-consumption.
  External event IDs and outbox keys support safe retry after an outbox failure.

### 4. New UC-5 behavior

- Generate/regenerate immutable, versioned report drafts from plan parameters,
  required-task results, rituals, metrics, incidents, corrective actions and
  authoritative pollen totals.
- Incomplete sources produce a draft listing blockers. Missing ritual success,
  unfinished required work, incomplete metrics/resources, and operational critical
  incidents prevent approval/completion.
- Re-read sources at confirmation; reject stale reports until regenerated.
- Chief Guardian approval seals the snapshot and completes an active season.
- A later presentation revision creates a new draft on a completed season without
  modifying earlier approved content. Exports continue to use the approved version
  until the revision is approved.
- Expose report viewing, approved-only HTML/PDF export **contracts**, versioned
  filenames/content types, and archival requiring an approved stored final report.
  Pending newer drafts block archival; archived seasons reject new mutations.
- Calculate pollen variance/ratio, incident counts, mean/critical deviations and
  ritual success rate from the snapshot. Historical comparison selects approved
  reports of the same type/catalog from the previous three calendar years; an
  empty result means comparison data is unavailable.

### 5. Domain changes and preservation evidence

Added distinct domain concepts: `RitualSession`, `ClimateMetric`,
`MetricDefinition`, `MagicIncident`, `CorrectionAction`, `PollenUsage`, and
`SeasonReport`.

Existing `SeasonPlan` received only new guarded activation, completion and
archival methods. Existing fields, factories, earlier transitions and serialization
shapes were not replaced. New audit action constants describe the new commands;
no audit update/delete operations were introduced. Existing task/reservation enums
were not expanded to force new behavior through old adapters.

Changes to working code were limited to responsibility extraction, moved imports,
the notification interface, and the three missing lifecycle operations. Evidence
was the specification's use-case separation, the concrete fake dependency in the
old service package, failing architecture tests, and new behavior/regression tests.
The original lifecycle, REST, security and migration-contract assertions remain
in place; their changes are package/import updates only.

Unchanged: frontend, HTTP routes/payloads, authentication/RBAC policy, database
migrations, Maven dependencies, CI configuration and existing adapter algorithms.

### 6. Deliberate integration boundary / remaining work

The new cores are not Spring beans and are not reachable through new HTTP routes.
Their production dependencies are interfaces, with test-only fakes used to execute
them. A later adapter increment must supply:

1. Plan-serialized transactions covering state, immutable audit appends and outbox
   inserts; durable outbox delivery/recipient resolution/retry processing.
2. UC-4/UC-5 repositories, uniqueness constraints and schema migrations. The
   existing JDBC audit/schema constraints do not support the new representations
   and must not be reused blindly.
3. Real launch evidence, required-work completion projections, and pinned
   catalog/rule versions. Legacy `READY` tasks are not completed work.
4. Authoritative Pollen Tree consumption/summary integration and idempotent retries.
   A local transaction cannot undo an already accepted external consumption.
5. Sensor transport, a scheduler invoking escalation, REST/security boundaries,
   and actual HTML/PDF renderers.

No permissive production transaction/readiness implementations or fabricated export
adapters were introduced. Unit fakes do not establish database atomicity or external
integration reliability.

**Pre-existing limitations deliberately retained:** the legacy approval flow still
shortcuts `SUBMITTED` to `APPROVED` and only explicitly blocks rejected pollen
reservations; preparation still uses the earlier `READY`-based checklist; local
reservation, calendar and notification stand-ins remain. These differ from parts
of ST-00. They were not silently redesigned during this restructuring, and must be
resolved with dedicated workflow/adapter regression tests before claiming full
ST-00/SRS compliance or wiring the new core into the running application.

### 7. Automated verification

Development used red/green cycles: architecture tests first failed on the old
layout/dependencies; missing-use-case tests first failed on missing types; focused
regressions then exposed and fixed participant revocation, incomplete failure
metadata, ritual event timing, forged incomplete report approval, report revision,
notification deduplication, composite import-key collisions and pending-report
archival cases.

Final verified environment: **Eclipse Temurin 17.0.20.1**, Maven 3.9.11, Windows.
The portable JDK was selected only for the test process; no global Java/PATH or
execution-policy settings were changed. Compiled application bytecode was checked
as major version **61 (Java 17)**.

| Check | Result |
| --- | --- |
| `mvn.cmd -f .\backend\pom.xml '-Dtest=!PostgresLifecyclePersistenceTest' clean verify` | **71 tests passed**, zero failures/errors; executable JAR packaged |
| Full Maven suite with the PostgreSQL class included | Blocked: Testcontainers cannot find a running Docker daemon; the same infrastructure failure existed before changes |
| `git diff --check` | Passed |

There are **33 additional test cases** relative to the non-Docker baseline of 38:
14 execution-use-case, 8 completion-use-case, 3 consumption-use-case, 4 execution
domain, 2 report domain, and 2 additional architecture tests.

The PostgreSQL test was **not disabled or weakened**. Run the full command from
[`README.md`](README.md) with Docker available before treating database compatibility
as verified. No live integration, rendering, performance or end-to-end UI claim is
made for the new cores.
