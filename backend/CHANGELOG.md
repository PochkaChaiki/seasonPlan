# Backend changelog

## Unreleased — Execution implementations and PostgreSQL runtime

- Implemented all seven requested contracts: `PinnedMetricRuleProvider`,
  `ConfiguredIncidentRuleProvider`, `ComputedRitualReadinessChecker`, and JDBC
  incident, correction-action, ritual-session and climate-metric repositories.
  Real beans now compose `RitualService`, `MonitoringService` and `IncidentResponseService`.
- Followed current SRS/UC and ST-00 conflict resolution. The operator selected
  validated versioned configuration **without defaults** and the existing
  temperature/humidity/pollen-density catalog. No lifecycle statuses were added.
- New plan creation requires complete configured execution/incident rules and
  finite targets for all three metrics. An immutable rules snapshot, plan and
  creation audit commit atomically; database protection prevents replacing the
  pinned snapshot. Copies and draft edits preserve selection; metric evaluation
  uses the displayed plan target and pinned tolerance/response settings.
- Computed readiness checks required categories/completed work, per-task confirmed
  compatible assignments with actual timestamps, required roles, authoritative
  pollen, manual flags and current critical incidents. Unverified approval
  conditions remain blockers. READY is not treated as completed work.
- Added category metadata to existing task DTOs, actual confirmation timestamps,
  shared per-plan transactions, and atomic `MAGIC_TASK_UPDATED` audit appends.
  Completion requires assignment evidence and cannot be selected at creation.
  Terminal tasks reject edits as structured validation; ongoing optional task
  work remains possible after readiness/activation, including during critical incidents.
- Forward migrations 012/013 preserve legacy data while adding global import and
  correction keys, same-plan incident links, exclusive ritual attempts/success,
  sealed participants, append-only ordered events and immutable terminal records.
  Incomplete history fails closed pending verified operator remediation—no
  guessed backfill, automatic deletion or audit-history rewrite.
- Fixed real-database replay defects: nanosecond sensor input is normalized before
  import comparison; an unchanged failed ritual remains replayable after a later
  success. Measurement decimal precision is retained.
- No new dependencies, execution REST routes, frontend flows, sensors, reporting
  or schedulers were added. Pollen remains external and the separate consumption
  outbox remains unconfigured. See the [configuration/rollout guide](../docs/architecture/EXECUTION_RULES_AND_PERSISTENCE.md)
  and [ADR 0005](../docs/decisions/0005-configured-execution-and-durable-repositories.md).

### Verification

- JDK **17.0.21**, Maven 3.9.11, PostgreSQL 14 via Testcontainers:
  `mvn.cmd -f .\backend\pom.xml '-Dapi.version=1.44' clean verify`
  with process-local `DOCKER_API_VERSION=1.44` for Docker 29 compatibility.
- **352 backend tests passed**, including **34 real PostgreSQL tests**;
  zero failures/errors/skips. Docker Desktop was started with operator approval,
  resolving the previous environment blocker. No production database was used.
- RED/GREEN regressions cover missing implementations/configuration, pinned
  snapshots/copying, creation rollback, readiness/assignment/completion guards,
  task audit/locking, terminal PATCH validation, semantic precision/replay,
  failed-attempt history, constraints and concurrent unique-key/ritual races.
- A real Spring/PostgreSQL acceptance test exercises preparation, successful
  ritual activation, critical measurement/incident/correction persistence and
  ongoing optional task completion. Core and persistence source reviews passed
  after their actionable findings were fixed and independently re-reviewed.
- Frontend remains unchanged; its **43 tests**, lint and production build pass.

## Earlier — Security feature and lifecycle authorization

- Kept account/authentication security as a sibling feature; it no longer imports
  lifecycle. Shared actor/role/provider contracts now live in `security/identity`.
  The separate administrator flag never becomes an eighth business role or grants
  implicit lifecycle privileges.
- Divided security into `api`, `domain`, `service`, `repository`, `exception`,
  `identity`, `infrastructure/jdbc`, `infrastructure/spring` and `config`.
  Moved the misplaced lifecycle SecurityConfiguration into security; security
  tests now mirror these responsibilities.
- Extracted account SQL, password hashing and real transaction adapters from the
  core account service. Commands belong to its interface and validate before use;
  neither public contracts nor the core depend on Spring principals/JDBC.
- Administrator creation is guarded at both service and HTTP boundaries.
  Self-credential updates bind the authenticated UUID, lock and verify the current
  stored hash (not a stale session hash), then atomically update username/hash.
  Session invalidation remains success-only; failed verification changes nothing.
- Added one framework-free lifecycle permission matrix for API role selection
  and service enforcement, plus resource-owner policy helpers. Removed independent
  controller role lists and role checks from field ValidationSupport.
- Fixed documented authorization gaps with red-first regressions: marking
  ritual-ready is coordinator-only (ST-00:31), and only a draft's creator-planner
  may edit/submit it (ST-00:179). Missing API roles consistently return 403.
- Preserved URLs/JSON shapes, CSRF/login/logout/session mechanics, dev-only
  initial administrator behavior, seven role values, business state transitions,
  pollen authority and audit/outbox transaction boundaries. No dependencies or
  database migrations changed.
- Existing lifecycle role/actor-ID parameters remain trusted internal-call
  contracts; the API binds them to the authenticated actor. They are not proof
  that arbitrary in-process input is authenticated.
- **Rollout requires a one-time sign-in**, as explicitly selected by the operator:
  old serialized principal/role class names moved. There is no legacy shim or
  automatic session deletion. Follow the coordinated session-only revocation
  procedure in [ST-01](../docs/architecture/ST-01-authentication-and-rbac.md#security-package-refactor-rollout).
- Architecture/merge rationale: [ADR 0004](../docs/decisions/0004-security-feature-and-lifecycle-authorization.md).

### Verification

- JDK **17.0.21**, Maven 3.9.11: `mvn.cmd -f .\backend\pom.xml '-Dtest=!Postgres*Test' clean verify`
  — **293 passed**, zero failures/errors/skips; backend JAR packaged.
- New tests cover framework/identity boundaries, administrator/business isolation,
  current stored password verification, credential ownership/session invalidation,
  SQL/hash/transaction adapters and composition, new-principal serialization,
  complete permission matrix, denied HTTP calls and foreign-planner mutations.
- Full `clean verify` was attempted: all 293 runnable tests passed, but both
  PostgreSQL test classes failed container initialization because the Docker
  daemon was unavailable (295 reported tests, zero assertion failures, two
  environment errors). The explicit non-PostgreSQL clean verify above then passed.
  New account integration tests cover duplicate/stale credentials and atomic
  user/credential/role rollback; those remain unexecuted alongside the retained
  lifecycle persistence checks. Unit transaction mocks are not database proof.
- Frontend: **43 tests passed**; lint and production build passed, with no frontend changes.
- Independent account/security and final authorization source reviews found no issues.

## Earlier — Responsibility-owned contracts and infrastructure

- Removed `lifecycle/application/port` and `application/shared`. Repository
  interfaces now live in `repository`, external client contracts in `integration`,
  notification contracts in `notification`, and lookup/access failures in `exception`.
- Kept service-owned transaction, actor-context, readiness/rule and report
  extension contracts in `service`. Moved `ValidationSupport` alongside its
  callers and made it package-private.
- Split `ExecutionPorts` and `CompletionPorts` into descriptive repository and
  service interfaces. Preserved all method semantics, immutable report/audit
  contracts and the nested records' defensive copying and configuration validation.
- Renamed the Natural Calendar contract/fake to `NaturalCalendarClient` and
  `AllowingNaturalCalendarClient`. The existing fake remains an educational stub.
- Moved concrete adapters into `infrastructure/jdbc`, `http`, `mail` and `fake`;
  updated Spring wiring, imports and matching test packages. No compatibility
  wrappers, new runtime services, dependencies or migrations were introduced.
- Preserved REST contracts, roles, statuses, domain behavior, pollen authority,
  command serialization and audit/outbox transaction boundaries.
- Added red-first package ownership tests and regression checks for nested
  readiness/rule records. Dependency checks protect domain isolation,
  framework-independent services/contracts and exclusive PollenService client access.
- Updated the package map and recorded placement rationale in
  [ADR 0003](../docs/decisions/0003-responsibility-owned-contracts-and-infrastructure.md).
  Existing native UML artifacts remain historical snapshots.

### Verification

- JDK **17.0.21**, Maven 3.9.11: non-PostgreSQL `clean verify` — **270 passed**,
  zero failures/errors/skips, backend JAR packaged.
- Full `clean verify` attempted: the same 270 tests passed, but the preserved
  `PostgresLifecyclePersistenceTest` could not initialize because the Docker
  daemon was unavailable. Real PostgreSQL rollback/concurrency validation remains
  outstanding; tests were not disabled or replaced with a permissive fake.
- Frontend: **43 tests passed**; lint and production build passed, with no frontend changes.
- Independent review found no migration-induced issues; `git diff --check` passed.

## Earlier — Cohesive lifecycle services

- Consolidated 13 lifecycle application services into 8 under `lifecycle/service`.
  `SeasonPlanService` now directly owns planning, review, checklist and readiness;
  `MagicTaskService` owns both tasks and fairy assignments. Removed the forwarding
  facade and planning-validation callback rather than retaining compatibility wrappers.
- Moved pollen, ritual, monitoring and incident services into the same package;
  renamed `CompletionService` to `SeasonReportService`, retaining report approval,
  completion and archival transaction boundaries.
- Removed application use-case subpackages. Kept ports/support, domain objects,
  persistence/integration adapters and authentication boundaries separate.
- Removed the unused `PlannerNotifications`/fake `NotificationService` path;
  review notifications continue through the durable outbox.
- Simplified Spring composition and REST controller dependencies from five
  application services to three. HTTP contracts, RBAC, lifecycle statuses,
  transactions, immutable audit history and pollen integration behavior remain unchanged.
- Migrated service tests, replaced stale architecture assertions with the new
  boundaries, and added task/assignment and REST preparation regression coverage.
- The original non-PostgreSQL baseline had one already-failing architecture test
  requiring application services not to import their existing audit service.
  The new architecture contract was verified red before consolidation.
- See [ADR 0002](../docs/decisions/0002-cohesive-lifecycle-services.md) for the
  evaluated merge options, rejected over-merges and dependency direction.

### Verification

- Eclipse Temurin **17.0.21**, Maven 3.9.11: `mvn.cmd -f .\backend\pom.xml '-Dtest=!PostgresLifecyclePersistenceTest' clean verify` — **266 passed**, backend JAR packaged.
- Full `clean verify` attempted: all 266 runnable tests passed, but
  `PostgresLifecyclePersistenceTest` could not start because no Docker environment
  was available. Database rollback/concurrency verification remains outstanding.
- Frontend: **43 tests passed**; lint and production build passed, with no frontend changes.
- Independent source review: no issues found. `git diff --check` passed.

## Earlier — Use-case-oriented clean architecture

Historical implementation notes below describe the previous layout and earlier
integration state. The service consolidation above and current `README.md`
supersede its package map and compatibility-facade descriptions.

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
