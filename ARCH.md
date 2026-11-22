# Proposed system architecture

## 1. Scope and source documents

This proposal uses only:

- [Use Case Specification](arch_docs/USE_CASE_SPECIFICATION.md), UC-1–UC-5.
- [System Requirements Specification](arch_docs/SYSTEM_REQUIREMENTS_SPECIFICATION.md), called **SRS** below.
- [Vision](arch_docs/VISION.md).

The existing implementation and project structure were not inspected or used. All structures below are proposals, not descriptions of existing code. Items marked **Open decision** need business or infrastructure agreement before implementation.

## 2. Architecture decision: modular monolith

Use a **client–server web application with a modular-monolith backend**.

- **Frontend:** React 18, JavaScript, HTML and CSS. Russian is the primary interface language.
- **Backend:** Java 17, Spring Boot 3.2 / Spring Framework 6, built with Maven.
- **Database:** one PostgreSQL 14 database.
- **Public API:** REST, JSON UTF-8, described with OpenAPI 3.0.
- **Deployment:** one backend application artifact. Initially, one application instance.
- **Module communication:** Java interfaces and method calls inside the same process, not internal HTTP APIs.
- **Background work:** scheduled jobs inside the same application, with durable work records in PostgreSQL.

**This is not a microservice architecture.** Modules do not have independent deployments, databases or network services.

The application can serve the built React files as static resources. Frontend source and backend source remain separate. The frontend accesses business data only through backend APIs. Node.js is required for frontend builds, not for production request handling.

### Why this fits

The main difficulty is business consistency, not independent service scaling:

- Approval depends on a confirmed pollen reservation.
- Readiness depends on tasks, assignments, resources and blockers.
- A critical incident must affect the season state immediately.
- Completion depends on work, incidents, ritual results and report data.
- Critical changes and their audit records must commit together.

One database permits local transactions for these rules. A modular monolith also limits deployment work on Helios. The stated load does not, by itself, justify distributed services.

Do not initially add a message broker, distributed cache, separate workflow engine, separate reporting service or time-series database. Do not use event sourcing. These are not needed to satisfy the described use cases.

## 3. System view

```text
Browser: React application
        |
        | HTTPS / REST / JSON
        v
+-------------------------------------------------------------+
| One Spring Boot application                                 |
|                                                             |
| Authentication / RBAC / REST endpoints                      |
|                      |                                      |
| Use-case workflows: planning -> preparation -> operation     |
|                      -> completion                          |
|                      |                                      |
| Season | Pollen | Work | Ritual | Monitoring | Response      |
| Reports | Reference data | Audit | Notifications             |
|                                                             |
| Persistence adapters | External adapters | Background jobs   |
+---------------+-------------------------+---------------------+
                |                         |
                | JDBC                    | HTTPS / WSS
                v                         v
          PostgreSQL 14          Pollen Tree API
                                 Natural Calendar API
                                 Climate metric sources
```

TLS terminates at a Helios-provided ingress, if available, or at the application HTTPS endpoint. The database is not accessible from browsers. External integration calls use API keys or mTLS.

WebSocket is optional. REST polling is sufficient for the initial user interface and the specified 1–5 minute metric refresh. A metric-source adapter can use WSS when the source requires it.

## 4. Backend modules and ownership

Organize the backend **by business capability first**, with layers inside each module.

| Module         | Owns                                                             | Main responsibilities                                                                                 |
| -------------- | ---------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| `season`       | Plans, revisions, decisions and season status                    | Parameter validation, revision history, permitted transitions, strategic parameter locking            |
| `pollen`       | Reservation records, demand calculation and consumption receipts | Coordinate the Pollen Tree API, confirm resources, compare actual usage with the reserve              |
| `work`         | Tasks, assignments and minimal participant records               | Required categories and roles, talent compatibility, participation confirmation, scheduling conflicts |
| `ritual`       | Ritual sessions, participant snapshots and ritual events         | Durable execution record, launch evidence, outcomes and failure reasons                               |
| `monitoring`   | Metric observations and calculated deviations                    | Manual entry, import, source ingestion, history and threshold evaluation                              |
| `response`     | Incidents and corrective actions                                 | Classification, ownership, deadlines, escalation and stabilization evidence                           |
| `reporting`    | Report versions and approval records                             | Aggregate season results, KPI, comparison, HTML/PDF export and archive access                         |
| `reference`    | Territory references and calendar-rule snapshots                 | Obtain external rules and expose the version used for validation                                      |
| `identity`     | Accounts, access roles and account-role links                    | Authentication and RBAC; no full fairy biography                                                      |
| `audit`        | Append-only audit entries                                        | Record critical changes and expose authorized read-only queries                                       |
| `notification` | Recipient notifications and delivery state                       | Approval requests, resource warnings, assignment reminders, deadlines and incident alerts             |
| `workflows`    | Cross-module use-case coordination                               | Execute UC-1–UC-5 and automatic reactions without moving business ownership between modules           |

`workflows` is a composition layer, not a generic workflow engine. For example, it checks readiness through module interfaces and then asks `season` to apply a permitted transition.

### Dependency rules

1. A module owns its tables and repositories. Other modules must not write those tables directly.
2. Modules expose small Java contracts in `api`. These are not REST endpoints.
3. Cross-module workflows depend on these public contracts, not on repository implementations.
4. Business modules must not depend back on `workflows`. Avoid circular dependencies.
5. Within a module, adapters depend on application interfaces; application logic depends on the domain and declared ports.
6. Domain classes do not depend on Spring, JDBC, HTTP clients or fake integrations.
7. A small `shared` package may contain identifiers, time abstractions and basic value types. It must not become a shared business model.
8. Read-only dashboard and report queries may join module-owned data through explicit query adapters. They must not bypass write-side rules.

For this application, Spring JDBC with explicit repository adapters is a suitable persistence choice. Use versioned database migrations, for example Liquibase, which is named in the SRS. Keep SQL and persistence mapping out of the domain.

## 5. Proposed domain model

### 5.1 Main entities

An aggregate is a consistency boundary, not a container for every object connected to a season. Store references between aggregates by identifier. Do not load all metrics, tasks and incidents to modify a `SeasonPlan`.

| Entity                   | Owner        | Important data and purpose                                                                                                                                                                                                |
| ------------------------ | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **SeasonPlan**           | `season`     | ID, season type, period, launch window, completion window, territory references, phenomenon parameters, metric targets, planner, status, business revision and concurrency version. Central business record.              |
| **SeasonPlanRevision**   | `season`     | Plan ID, revision number, parameter snapshot, author, time and reason. Preserves the parameters used for a decision, calculation or report.                                                                               |
| **SeasonDecision**       | `season`     | Plan revision, decision type, decision maker, time, outcome, comments and conditions. Records approval, return for changes, launch authorization and completion decisions where required.                                 |
| **Territory**            | `reference`  | Stable ID, external calendar reference, name and time-zone reference. No geographical or physical simulation.                                                                                                             |
| **CalendarRuleSnapshot** | `reference`  | External rule version, validity interval, fetch time, scope and rule data. Records which external rules were used; does not replace the calendar as authority.                                                            |
| **PollenReservation**    | `pollen`     | Season ID, plan revision, calculated demand, requested and confirmed amounts, unit, business status, external reservation ID, confirming user and rejection reason. Local record of the external resource agreement.      |
| **PollenConsumption**    | `pollen`     | Reservation ID, quantity, occurrence time, actor, external receipt and operation reference. Records consumption accepted by the Pollen Tree API.                                                                          |
| **MagicTask**            | `work`       | Season ID, category, required talent, planned execution interval, deadline, mandatory flag, status and result. Aggregate root for task assignments.                                                                       |
| **FairyAssignment**      | `work`       | Task ID, participant ID, task role, assigned interval, confirmation status and confirmation time. Child entity of `MagicTask`.                                                                                            |
| **FairyParticipant**     | `work`       | ID, display name, talent references, relevant availability and optional user-account ID. Minimal operational participant, not a detailed character profile.                                                               |
| **RitualSession**        | `ritual`     | Season ID, approved plan revision, planned and actual times, launch outcome, completion outcome, failure reason and readiness-check reference. Separate aggregate because a ritual can last 24–72 hours.                  |
| **RitualParticipant**    | `ritual`     | Session ID, participant ID, role and confirmation evidence captured at launch. Historical snapshot, not a live link to a mutable assignment list.                                                                         |
| **RitualEvent**          | `ritual`     | Session ID, time, event type, actor/source and event details. Append-only ritual protocol.                                                                                                                                |
| **ClimateMetric**        | `monitoring` | Season and territory IDs, observation and receipt times, temperature, humidity, migration index, source and source record ID. May carry a persisted deviation assessment and its policy/target versions.                  |
| **MagicIncident**        | `response`   | Season ID, type, severity, status, detection time, cause, consequences, responsible user and resolution evidence. Separate aggregate.                                                                                     |
| **CorrectionAction**     | `response`   | Season ID, optional incident reference, originating metric/trigger reference, responsible person, deadline, status, result and escalation history. Can exist without an incident when a deviation requires correction.    |
| **SeasonReport**         | `reporting`  | Season ID, report version, source-data revision or cutoff, presentation parameters, KPI, resource totals, ritual results, deviations, incidents, approval state and approval details. Each approved version is immutable. |

The documents explicitly name reservation states `DRAFT` and `APPROVED`; they also require rejection. An integration call that is still pending must not be presented as an approved reservation.

Except for the specified season states and named reservation states, detailed entity status sets remain a design decision. Do not copy the season state machine into every entity.

### 5.2 Value objects and policies

Use value objects for data without an independent lifecycle:

- `TimeWindow`: start and end, with a documented boundary convention.
- `PollenQuantity`: non-negative decimal quantity and unit; use grams consistently at the application boundary.
- `MetricTarget`: metric kind, target/range, territory scope and relevant period.
- `DeviationAssessment`: score, classification, target revision and calculation-policy version.
- `ReadinessResult`: passed checks, failed checks, reasons and the versions checked.
- `ApprovalCondition`: a condition that must be satisfied before launch.

Use explicit domain policies for calendar compliance, pollen demand, deviation calculation, mandatory task categories, mandatory roles and permitted operations in an emergency.

**Do not invent formulas or thresholds.** The three documents do not define the pollen calculation, `deviation_score`, KPI formulas or the complete seasonal rule set. Keep these policies replaceable and versioned. A deterministic educational policy must be identified as a temporary implementation, not as an established business rule.

### 5.3 Supporting records

These records support the application but are not additional fairy-world concepts:

| Record | Purpose |
|---|---|
| `UserAccount`, `AccessRole`, `UserRole` | Authentication and access permissions |
| `ReadinessCheck` | Saved evidence of a readiness evaluation, including plan and dependency versions |
| `AuditEntry` | Immutable time, actor, action, object, relevant change details, correlation ID and payload hash |
| `Notification` | Recipient, cause, message, creation time, read state and delivery state |
| `OutboxMessage` | Durable request for notification delivery or another retryable background operation |
| `IntegrationOperation` | External operation key, request reference, result, attempts and reconciliation state |

Keep **access role**, **task role** and **talent** separate. A water talent is not an authorization role. An assigned participant need not have a login unless direct confirmation or other user actions are required.

### 5.4 Main relationships

```text
SeasonPlan 1 ---- * SeasonPlanRevision
SeasonPlan 1 ---- * SeasonDecision
SeasonPlan * ---- * Territory
SeasonPlan 1 ---- * PollenReservation       [historical revisions]
PollenReservation 1 ---- * PollenConsumption
SeasonPlan 1 ---- * MagicTask
MagicTask 1 ---- * FairyAssignment * ---- 1 FairyParticipant
SeasonPlan 1 ---- * RitualSession           [attempts/history]
RitualSession 1 ---- * RitualParticipant
RitualSession 1 ---- * RitualEvent
SeasonPlan 1 ---- * ClimateMetric * ---- 1 Territory
SeasonPlan 1 ---- * MagicIncident
SeasonPlan 1 ---- * CorrectionAction
MagicIncident 0..1 ---- * CorrectionAction
SeasonPlan 1 ---- * SeasonReport            [versions]
```

Allow at most one effective reservation for the relevant plan revision and at most one running launch ritual per season. Historical reservations and failed ritual attempts remain available.

## 6. Season lifecycle and business controls

Keep the fixed SRS state set:

```text
DRAFT, SUBMITTED, VERIFIED, APPROVED, RITUAL_READY,
ACTIVE, CORRECTION, EMERGENCY, COMPLETED, ARCHIVED, CANCELLED
```

The following is a **proposed transition policy**, not a claim that the documents define every transition:

| Transition or action | Required control |
|---|---|
| `DRAFT -> SUBMITTED` | Required fields, valid intervals, calendar compliance and no forbidden window conflict |
| `SUBMITTED -> VERIFIED` | Record successful verification, if verification is a separate persisted step |
| `SUBMITTED / VERIFIED -> APPROVED` | Chief Keeper decision and confirmed pollen reservation for the current plan revision; UC-2 starts from `SUBMITTED` |
| `SUBMITTED / VERIFIED -> DRAFT` | Return for changes with a reason; preserve decision history |
| `APPROVED -> RITUAL_READY` | Required tasks and roles exist, required assignments are confirmed, resource conditions and additional approval conditions are met |
| Start a ritual | Recheck current readiness and launch authorization; do not trust a saved green checklist |
| `RITUAL_READY -> ACTIVE` | Explicit evidence of ritual success under the agreed success definition |
| `ACTIVE -> CORRECTION` | A deviation requires corrective work under the agreed threshold policy |
| Operational state `-> EMERGENCY` | A qualifying critical incident is registered; notify responsible users |
| `CORRECTION -> ACTIVE` | Required stabilization checks pass |
| `EMERGENCY -> ACTIVE / CORRECTION` | Authorized recovery decision, no unresolved critical blockers, and stabilization checks pass |
| `ACTIVE / CORRECTION -> COMPLETED` | Ritual and mandatory work are complete, critical incidents are closed, report draft exists and the Chief Keeper confirms results |
| `COMPLETED -> ARCHIVED` | Required report version is approved and durably saved |
| `... -> CANCELLED` | Only from states and under conditions explicitly agreed with the business |

There must be no general-purpose endpoint that sets an arbitrary status. Expose business commands such as submit, approve, mark ready, start ritual, confirm completion and archive.

Additional controls:

- Every accepted state transition and critical decision creates an audit entry in the same database transaction.
- Material edits after approval must not silently retain approval for changed parameters. Use revision-bound approval and an explicit return/reapproval process.
- Reservation changes must trigger resource and readiness revalidation. An old confirmation cannot authorize an increased volume.
- Launch checks must use current task, assignment, blocker and resource data.
- In `EMERGENCY`, block the agreed set of dangerous operations, but allow incident handling, diagnostics and approved mitigation.
- Closing one incident does not restore the season if another critical incident remains open.
- Treat operational data in completed/archived seasons as frozen. Report presentation changes create new report versions; they do not rewrite approved results.

**Open decisions:** the exact purpose of `VERIFIED`, return-to-preparation transitions, cancellation rules, conditional approval rules and the recovery authorization matrix are not fully specified.

## 7. Transactions, concurrency and background work

### 7.1 Local consistency

Use one local PostgreSQL transaction for each business command and its audit entries.

For commands that affect season readiness, status, incidents or closure, acquire the season row lock before checking dependent data. All such mutation paths must follow this rule, including background paths. This prevents a task or reservation from changing between a readiness check and a launch decision.

Use a concurrency version to reject stale browser edits. Scheduling checks also need serialization for the affected participant/time scope so two seasons cannot assign the same participant concurrently without detecting a conflict. Use a consistent lock order.

Metric ingestion need not lock the season for every observation. However, a qualifying metric-triggered incident, resulting season transition and audit record must be committed together. Recheck the current season state before applying the reaction.

Use foreign keys, non-null checks, unique constraints and quantity checks as a second protection layer. Cross-row window conflicts require transactional serialization or a suitable PostgreSQL exclusion constraint; a pre-save query alone is not safe.

**Open decision:** the documents do not define whether window conflicts are global or limited to shared territories, or whether touching interval boundaries count as overlap. Do not encode an assumed scope as a final rule.

### 7.2 External pollen operations

The **Pollen Tree API is the resource authority**. Vision requires all pollen operations to pass through that API. The local database stores requests, external confirmations and receipts; it must not maintain an independently authoritative stock balance.

A PostgreSQL transaction cannot atomically commit a remote REST call. Use a durable integration-operation record:

1. Save the requested operation and stable operation key locally.
2. Call the external API without holding a long database transaction or season lock.
3. Record the confirmed result in a short transaction. Recheck the plan revision before applying it.
4. On timeout, record an unknown/pending outcome. Reconcile before retrying a non-idempotent operation.
5. If a result belongs to an obsolete plan revision, do not mark the current plan funded. Reconcile or release the obsolete external reservation through the API.

Use external idempotency keys and status queries where supported. Their availability is an **open integration-contract decision**. Never assume exactly-once remote execution.

If resource confirmation is missing, stale under the agreed freshness policy, or unresolved, block approval/launch. Do not mark an operation successful merely to keep the workflow moving.

### 7.3 Rituals and scheduled work

A ritual lasting 24–72 hours must not hold an HTTP request or database transaction open.

- A start command validates readiness and saves a durable session.
- Later commands/events record progress, successful launch evidence, completion or failure.
- A retry of the same start command returns the same operation/session result.
- Restarting the application must not lose session progress.

Use database-backed scheduled work for metric imports, notifications, deadline warnings, correction escalation, integration reconciliation and report jobs. Save outbox records in the transaction that creates the work. Workers claim records and retry with bounded backoff. Consumers must handle duplicate delivery safely.

Outbox delivery is for retryable side effects. It must not delay a mandatory emergency transition or the audit record for an accepted command.

## 8. Reporting and audit

### Reporting

Build reports from a consistent source snapshot and record its cutoff/revisions. For an expensive export, save the snapshot first and render outside the critical transaction. Before approval or completion, verify that relevant inputs have not changed.

Suggested completion sequence, consistent with SRS FR-71–FR-77:

1. Check completion conditions and generate a report draft.
2. The planner reviews the draft.
3. The Chief Keeper confirms season results; the season becomes `COMPLETED`.
4. Approve and save the report version, then archive the season.

Whether result confirmation and report approval are one decision or two is an **open decision**. Preserve both meanings in the model. Never archive an unapproved report. Never regenerate an approved report in place.

### Audit protection

- Use a dedicated audit table/schema and an application role that can insert and read, but cannot update, delete or truncate audit data.
- Keep schema ownership and migration privileges separate from application credentials.
- Add database-level rejection of prohibited audit mutations. The application role must not be able to disable that protection.
- Save audit data synchronously with business changes. If the audit insert fails, roll back the command.
- Include system actors for scheduled actions, not a fabricated human user.
- Store the canonical payload and its hash for integrity checks. A hash alone is not protection from a privileged attacker who can rewrite both.

**Limit:** PostgreSQL superusers or operating-system administrators can bypass ordinary database protections. Vision's absolute ban on administrator modification cannot be guaranteed by one mutable database alone. Independently protected copies and integrity evidence are needed for stronger assurance; the required threat model must be agreed.

Four-hour backups also do not guarantee zero audit loss after total storage failure. Use durable commits, backups at least every four hours, WAL protection and restore tests. A strict zero-loss requirement for acknowledged writes across a storage disaster requires synchronous protected replication or equivalent infrastructure. Do not claim that a single instance plus periodic backups provides this guarantee.

## 9. API and interface design

Use `/api/v1` as the REST boundary. Representative resources and commands:

```text
GET/POST   /api/v1/seasons
GET/PATCH  /api/v1/seasons/{id}
POST       /api/v1/seasons/{id}/submit
POST       /api/v1/seasons/{id}/approve
GET        /api/v1/seasons/{id}/readiness
POST       /api/v1/seasons/{id}/mark-ready
POST       /api/v1/seasons/{id}/ritual-sessions
POST       /api/v1/seasons/{id}/confirm-completion
POST       /api/v1/seasons/{id}/archive

/api/v1/seasons/{id}/pollen-reservations
/api/v1/seasons/{id}/tasks
/api/v1/tasks/{id}/assignments
/api/v1/assignments/{id}/confirm
/api/v1/seasons/{id}/metrics
/api/v1/seasons/{id}/incidents
/api/v1/seasons/{id}/correction-actions
/api/v1/seasons/{id}/reports
/api/v1/audit
/api/v1/notifications
```

This is an API outline, not a complete endpoint contract.

- Paginate lists and histories. Filter seasons by status, territory and user, as required by SRS FR-82–FR-85.
- Return stable error codes, understandable Russian messages and failed readiness checks.
- Return a concurrency conflict for a stale edit; do not overwrite newer work.
- Apply idempotency controls to retry-prone creation and external-operation commands.
- Return a durable operation reference for genuinely long-running work.
- The dashboard may use one read-only summary endpoint rather than many browser requests.
- Client-side validation improves usability. The backend remains authoritative.

For the initial same-origin application, Spring Security with server-side sessions is a suitable authentication choice. Use secure, HttpOnly cookies, CSRF protection and backend authorization on every command and protected query. Hiding a button is not authorization. Keep secrets outside source control and audit payloads.

## 10. Proposed project structure

The following tree is designed from the requirements only. It does not describe or require reuse of the current project layout.

```text
/
├── ARCH.md
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/<base-package>/
│       │   │   ├── Application.java
│       │   │   ├── bootstrap/          # Configuration and adapter wiring
│       │   │   ├── workflows/          # UC orchestration and cross-module commands
│       │   │   ├── season/
│       │   │   ├── pollen/
│       │   │   ├── work/
│       │   │   ├── ritual/
│       │   │   ├── monitoring/
│       │   │   ├── response/
│       │   │   ├── reporting/
│       │   │   ├── reference/
│       │   │   ├── identity/
│       │   │   ├── audit/
│       │   │   ├── notification/
│       │   │   └── shared/             # Small technical/value-type foundation
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/changelog/        # Versioned database migrations
│       └── test/
│           ├── java/<base-package>/    # Unit, workflow and integration tests
│           └── resources/             # Test inputs and deterministic fixtures
├── frontend/
│   ├── package.json
│   └── src/
│       ├── app/                       # Routes, session and application shell
│       ├── pages/                     # Dashboard, season, tasks, incidents, etc.
│       ├── features/
│       │   ├── season-planning/
│       │   ├── season-approval/
│       │   ├── season-preparation/
│       │   ├── ritual-control/
│       │   ├── climate-monitoring/
│       │   ├── incident-response/
│       │   └── season-completion/
│       └── shared/
│           ├── api/                   # REST client and transport errors
│           ├── ui/                    # Reusable presentation components
│           └── i18n/                  # Russian interface messages
├── deployment/
│   ├── helios/                        # Native Java deployment and recovery notes
│   └── docker/                        # Container build and local/CI environment
└── .github/workflows/                 # Java 17 build, tests and frontend checks
```

Typical business-module structure:

```text
season/
├── api/                    # Public Java contracts and contract DTOs
├── domain/                 # Aggregates, value objects and business policies
├── application/            # Module use cases; implements public contracts
│   └── port/               # Required repositories and external capabilities
└── adapter/
    ├── in/web/             # Module-local REST endpoints, where appropriate
    └── out/
        ├── persistence/    # JDBC repositories and row mapping
        └── external/       # Only where this module needs external adapters
```

Cross-module endpoints belong to `workflows` and call the relevant module APIs. Do not create empty layers or adapters where they have no use. Start with **one Maven backend module**, not one Maven artifact per business module.

Frontend features mirror user activities, not database tables. They must not implement independent approval, resource-allocation or status-transition rules.

## 11. Deployment and operation

### Helios production target

Deploy the application JAR under OpenJDK 17 on FreeBSD 14.4-STABLE amd64. Serve the compiled frontend from the same application unless available hosting requires a separate static origin. Use PostgreSQL 14 and an HTTPS endpoint.

The documents require both FreeBSD deployment and Docker support. **Do not assume Linux Docker containers can run natively on the specified FreeBSD host.** Provide Docker packaging for a compatible local/CI environment; use native Java deployment on Helios unless an approved compatible container host is available. Actual account permissions, TLS provisioning, database placement and backup destinations remain infrastructure decisions.

Build and test with Java 17, not merely a newer local JDK with source compatibility flags. Keep production settings and credentials external to the JAR.

### Performance and growth

- Index season status, territory membership and responsible-user searches.
- Index metrics by season, territory and observation time.
- Index open incidents, unfinished tasks and correction deadlines.
- Use bounded imports, batch inserts and paginated histories.
- Test the three-second main-screen target and five-second standard-operation/save target.
- Treat 1,000 metrics per season as a minimum supported volume, not a storage limit.
- Measure retention and ingestion volume before adding partitions or precomputed report tables.

Start with vertical scaling. If measurement requires multiple application instances, deploy replicas of the **same monolith**. Use shared session storage or another agreed session strategy, and database-backed job claiming so replicas do not duplicate scheduled work. This does not require service decomposition.

## 12. Requirement differences and open decisions

Use specific SRS functional rules as the design baseline where broader descriptions differ. This is a proposal, not an established document-precedence rule. Keep the following issues visible:

| Issue                          | Document difference or gap                                                                                                                   | Proposed treatment                                                                                                                             |
| ------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Emergency severity             | SRS FR-65 requires notification for `HIGH`/`CRITICAL`; FR-66 requires emergency for critical incidents. Vision §5.6 says emergency for both. | Notify for both; guarantee automatic emergency for `CRITICAL`. Agree the `HIGH` policy before implementation.                                  |
| Ritual success                 | UC-4 and Vision discuss successful start; SRS FR-11/45 require a successful ritual. Vision says the ritual may last 24–72 hours.             | Record launch and full-session outcomes separately. HTTP acceptance is never success evidence. Agree which confirmed outcome permits `ACTIVE`. |
| Verification                   | The state exists, but UC-2 begins approval from `SUBMITTED`.                                                                                 | Do not invent an extra human approver. Decide whether verification is automatic, an explicit step, or optional.                                |
| Launch authority               | UC-4 makes the coordinator the launch actor; Vision assigns final launch sanction to the Chief Keeper.                                       | Separate Chief Keeper authorization from coordinator execution; agree how authorization is recorded and invalidated.                           |
| Report approval order          | Completion and report approval overlap in UC-5 and SRS FR-71–FR-77.                                                                          | Preserve result confirmation and report approval separately; agree whether one user action performs both.                                      |
| Capacity                       | SRS requires at least 50 active users and 100 events/minute. Vision also specifies 100 active users and 500 metrics/minute.                  | Size and test the monolith against the higher stated user and metric loads until clarified. Define sustained duration and retention.           |
| Response times                 | Vision §7 permits slower operations than the SRS and Vision §9.3.                                                                            | Use the stricter three-second screen and five-second standard-operation/save targets.                                                          |
| Framework naming               | Vision §4.5 says “Spring Framework 3.2”; §6 says Spring Boot 3.2.                                                                            | Use Spring Boot 3.2 with its compatible Spring Framework 6 generation on Java 17.                                                              |
| External authority             | Vision requires every pollen operation to use the Tree API; the SRS also requires local records and manual user actions.                     | User actions create API commands; local records reflect results. Manual UI entry does not bypass the API.                                      |
| Missing business rules         | Demand/score/KPI formulas, thresholds, conflict scope and complete transitions are absent.                                                   | Use explicit policies; agree rules or document deterministic educational substitutes.                                                          |
| Immutable audit and zero loss  | Absolute administrator immutability and zero loss exceed what one ordinary database plus four-hour backups can guarantee.                    | Agree the threat/failure model and provide the required independent protection and durability.                                                 |
| Terminal data and late metrics | Late observations, post-completion corrections and report-source amendments are not specified.                                               | Freeze accepted operational results by default; agree a controlled amendment policy instead of silently rewriting history.                     |
| Calendar outage                | Rules must be current, but maximum snapshot age is unspecified.                                                                              | Agree freshness limits; block calendar-dependent acceptance when no valid rule version is available.                                           |

## 13. Implementation approach and verification

Build complete use-case slices, not all database tables first:

1. **UC-1:** planning, calendar validation, history, submission and audit.
2. **UC-2:** pollen confirmation, verification, approval decisions and notifications.
3. **UC-3:** tasks, participants, confirmations, scheduling and readiness.
4. **UC-4:** durable ritual, metrics, corrections, incidents and emergency control.
5. **UC-5:** completion checks, report versions, approval, export and archive.

Use deterministic adapters for unavailable external systems. They must implement the same ports as real adapters and follow the same acceptance tests. The fake Pollen Tree remains behind the API abstraction; it must not introduce a second resource-accounting path.

For production implementation, use red–green–refactor TDD:

- Unit tests for policies, calculations, transitions and readiness rules.
- PostgreSQL integration tests for constraints, concurrency, migrations and audit immutability.
- API/RBAC tests for allowed and denied operations.
- Contract tests for real and fake integration adapters.
- Workflow tests for approval without reserve, failed launch, critical incident handling, recovery, closure and archive.
- Restart/retry tests for rituals, external operations and outbox work.
- Load tests for the agreed user/event/metric targets and restore tests for protected data.

**Result:** one deployable backend, one database and clear business-module boundaries, with consistent season control and replaceable external integrations—without microservices.
