# Cohesive lifecycle services instead of use-case packages

Status: Accepted. Package locations are refined by
[ADR 0003](0003-responsibility-owned-contracts-and-infrastructure.md); service
ownership decisions remain unchanged.

## Context

The lifecycle core had 13 application service classes. `SeasonPlanService` only
constructed and forwarded to planning, approval and preparation services. Those
services repeatedly held the same plan repository, clock, transaction and audit
dependencies. Approval received planning validation through a callback. Task and
assignment commands were split despite sharing task validation/scheduling data.

Use cases remain useful requirements and test scenarios, but do not need their
own Java package or service. A physical boundary should protect a responsibility,
not simply mirror a workflow step.

## Decision

Keep eight cohesive application services together in
`com.seasonplan.lifecycle.service`, scoped to the existing lifecycle module:

| Service | Ownership / consolidation |
| --- | --- |
| `SeasonPlanService` | Directly owns drafting, calendar/conflict validation, submission, review decisions/history, checklist and preparation readiness. Merges `PlanningService`, `ApprovalService`, `PreparationService` and `ReadinessChecklistService`; no forwarding facade or validation callback remains. |
| `MagicTaskService` | Task creation/update/read, fairy assignments, talent compatibility and schedule conflict checks. Merges `FairyAssignmentService`; domain entities and repositories stay distinct. |
| `PollenService` | Reservation and consumption commands/reads, audit and usage warnings. Sole caller of the Pollen Tree client port. |
| `RitualService` | Ritual attempts, participant snapshots, events and guarded activation. |
| `MonitoringService` | Metric ingestion/history, import idempotency and automatic deviation responses. |
| `IncidentResponseService` | Incident lifecycle, manual corrective work and escalation. |
| `SeasonReportService` | Renamed `CompletionService`: report generation, immutable approved snapshots, export/history, atomic approval/completion and report-gated archival. |
| `AuditLogService` | Append-only audit writes and reads. |

`ServiceSupport` is a package-private helper, not another service or facade.
At adoption, `application/port` and `application/shared` retained framework-independent
contracts, validation and exceptions, while API, adapter, persistence, configuration
and domain packages stayed separate. ADR 0003 subsequently distributes those
contracts by responsibility and moves concrete adapters into `infrastructure`;
there are now no application, port or shared source packages.
Authentication/administration services remain inside the independent `security`
boundary; this is not a repository-wide bucket for every class named Service.
The unused `PlannerNotifications`/in-memory `NotificationService` path is removed;
review notifications already use the durable outbox and delivery adapters.

## Merge options deliberately rejected

- **Tasks into SeasonPlanService:** task editing, assignment compatibility and
  scheduling form a coherent responsibility with their own repositories and
  coordinator authorization. Merging them would make the plan service broader
  without eliminating meaningful orchestration.
- **Readiness into MagicTaskService:** readiness also validates pollen authority,
  plan checklist flags and approval-condition evidence. It belongs with plan
  preparation, not just task/assignment commands. The plan service reads the task
  and assignment ports directly, with no circular plan/task service dependency.
- **Rituals, metrics, incidents or reports into SeasonPlanService:** these own
  session/event history, import keys, response rules or immutable snapshots and
  exporters. Combining them would introduce many unrelated dependencies. Ritual
  activation and report approval/completion stay atomic within their existing
  command transactions; domain methods still enforce every plan transition.
- **Monitoring into IncidentResponseService:** measurements produce incidents but
  have distinct ingestion, unit/catalog validation and replay semantics. Both
  retain shared repository ports rather than a new forwarding service.
- **Pollen or audit into another service:** external pollen authority and
  immutable audit history are explicit business boundaries, not shallow wrappers.

## Dependencies and compatibility

API/configuration depend on services; services depend on domain types and ports.
Adapters implement ports and are composed in configuration. Neither services nor
domain objects import Spring, SQL, serialization or concrete integrations.
`SeasonPlanService`/`SeasonReportService` access pollen only through `PollenService`.

This is a behavior-preserving consolidation, not a new persistence or workflow
slice. Existing HTTP routes, payloads, roles, lifecycle statuses, domain entities,
migrations and integration contracts are unchanged. Removed Java service classes
are intentionally not retained as deprecated forwarding wrappers; internal callers,
Spring bean definitions and tests use the new owners directly.

Existing command transaction scopes, row locking, review plan/audit/outbox
atomicity, pollen-provider authority/replay behavior and report snapshot checks are
preserved. Legacy task/assignment commands have not gained new transaction or
audit behavior implicitly. Core-only execution/reporting services are still not
wired into a permissive production runtime. Existing deterministic adapter fakes
and fail-closed integration gaps remain documented in `backend/README.md`.

## Validation

Architecture tests require the consolidated service inventory, no use-case service
packages or forwarding facade, framework-independent services/contracts, isolated
domain logic and exclusive pollen-client access through `PollenService`.
Behavioral tests exercise the new owners directly, including task/assignment
validation, scheduling, RBAC, readiness, review audit/outbox effects, ritual
activation and report completion/archival. REST regression tests keep task,
assignment and preparation endpoints compatible.

The new package contract was first run against the old layout and failed for the
expected service/package/controller dependencies. A separate red test preceded
removal of the unused notification path. Verification uses JDK 17. PostgreSQL
rollback/concurrency tests still require a running Docker daemon; unit tests and
composition tests are not a substitute for that integration gate.
