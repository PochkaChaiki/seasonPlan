# ST-00: Specification resolution

Date: 2026-08-02

This document is the authoritative business decision record for ST-00. It resolves
conflicts in the requirements; it does not add a later-use-case implementation.
The executable SeasonPlan transition matrix is
`backend/.../domain/SeasonPlanStatus.java`; its complete allowed/forbidden matrix
is protected by `SeasonPlanStatusTest`.

## SeasonPlan lifecycle transition table

The only SeasonPlan statuses are `DRAFT`, `SUBMITTED`, `VERIFIED`, `APPROVED`,
`RITUAL_READY`, `ACTIVE`, `COMPLETED`, and `ARCHIVED`. `CORRECTION`,
`EMERGENCY`, and `CANCELLED` are not SeasonPlan statuses, database values, API
enum values, or UI options.

`Correction` and `emergency` mean, respectively, an open CorrectionAction and
the calculated **critical operating condition** (at least one open CRITICAL
MagicIncident). They are operational conditions owned by incident handling.
They do not change lifecycle status. Resolving the last qualifying incident
clears the condition without a lifecycle transition.

| Source | Target | Authorized actor / command owner | Guard | Audit action | Failure and recovery |
| --- | --- | --- | --- | --- | --- |
| DRAFT | SUBMITTED | Fairy Planner | Complete, valid plan; Natural Calendar and territory-window conflict checks pass | `SEASON_PLAN_SUBMITTED` | Keep DRAFT; return field errors. Planner corrects and retries. |
| SUBMITTED | VERIFIED | Chief Guardian | Review is complete; plan remains valid | `SEASON_PLAN_VERIFIED` | Keep SUBMITTED; return review error. Guardian may retry. |
| SUBMITTED | DRAFT | Chief Guardian | Non-blank return-for-revision comment | `SEASON_PLAN_RETURNED` | Keep SUBMITTED; Guardian supplies comment. |
| VERIFIED | APPROVED | Chief Guardian | Latest authoritative Pollen Tree reservation is `CONFIRMED`; all standard review checks pass | `SEASON_PLAN_APPROVED` | Keep VERIFIED; report the missing/non-confirmed reservation or failed review. Restore/correct it and review again. |
| VERIFIED | DRAFT | Chief Guardian | Non-blank return-for-revision comment | `SEASON_PLAN_RETURNED` | Keep VERIFIED; Guardian supplies comment. |
| APPROVED | RITUAL_READY | Talent Coordinator | Computed standard checklist and, where applicable, extended conditional-approval checklist pass | `SEASON_MARKED_RITUAL_READY` | Keep APPROVED and return every unmet checklist key. Clear blockers and retry. |
| RITUAL_READY | ACTIVE | Ritual-session application service | A newly completed successful RitualSession with a successful command-time readiness recheck exists | `SEASON_PLAN_ACTIVATED` | Persist a failed session when one started; keep RITUAL_READY. Correct the failure and start a permitted retry. |
| ACTIVE | COMPLETED | Chief Guardian | Successful ritual, required tasks/actions complete, no open CRITICAL incident, and final report is confirmed | `SEASON_PLAN_COMPLETED` | Keep ACTIVE; return all blockers. Correct source data/report and retry. |
| COMPLETED | ARCHIVED | Chief Guardian | Immutable approved report snapshot is durably stored | `SEASON_PLAN_ARCHIVED` | Keep COMPLETED; approve/store report and retry. |

Every listed transition, the audit append, and state persistence must commit in
one transaction. An audit/persistence failure rolls back the transition. A
notification is published from an after-commit outbox; a delivery failure retries
the outbox record and never reverses a committed transition. All other pairs,
including self-transitions and every transition from ARCHIVED, are forbidden.

`VERIFIED` is entered only by the Chief Guardian through the SUBMITTED review
command. It is not a client-selected value. Approval can only start from
VERIFIED. The current implementation gap is delivered by ST-02; the matrix is
already executable and tested so ST-02 must conform to it.

## Review, pollen, tasks, and readiness

### Conditional approval and checklists

A Chief Guardian may approve with zero or more named conditions. This is an
approval decision attribute, not a ninth status. Each condition has an immutable
key, Russian display text, evidence requirement, and an owner role. Standard
readiness is the computed set of `pollen-confirmed`, required task categories,
completed required tasks, confirmed required roles, and no critical operating
condition. A conditional approval adds its condition keys to that computed set;
manual checklist completion cannot bypass a rule.

Example: approval with condition `weather-calibration-evidence` remains
`APPROVED`; even with all standard checks true, RITUAL_READY is rejected until
evidence is attached and its rule evaluates true.

### Season-type requirements

The versioned Season Requirement Catalog is selected when a plan is created and
is retained by version on the plan. Initial catalog values are:

| Season type | Required task categories | Required confirmed roles |
| --- | --- | --- |
| SPRING | `WEATHER`, `POLLEN` | `RITUAL_LEAD`, `WEATHER_WEAVER` |
| SUMMER | `WEATHER`, `WATER` | `RITUAL_LEAD`, `WATER_KEEPER` |
| AUTUMN | `HARVEST`, `WEATHER` | `RITUAL_LEAD`, `HARVEST_WARDEN` |
| WINTER | `FROST`, `SHELTER` | `RITUAL_LEAD`, `FROST_WARDEN` |

For example, a SPRING plan with a completed WEATHER task but no Pollen task is
not ready; a confirmed `WEATHER_WEAVER` does not substitute for
`RITUAL_LEAD`. A catalog change creates a new version and never changes a plan
already created with an earlier version.

### PollenReservation

The canonical reservation statuses are `DRAFT`, `CONFIRMED`, and `REJECTED`.
`CONFIRMED` is the only authoritative reservation state; “approved reservation”
is a prohibited synonym. Legal transitions are `DRAFT -> CONFIRMED` and
`DRAFT -> REJECTED`; terminal reservations do not change.

After a planner submits, the Pollen Guardian can create the calculated DRAFT
through the Pollen Tree boundary. Confirmation occurs before UC-2 approval,
removing the former circular timing. UC-3 reads the confirmed reservation and
may request an amendment. Because a confirmed reservation is immutable, an
amendment creates a linked replacement DRAFT; the prior record remains intact
and becomes superseded only when its replacement is CONFIRMED. The authoritative
reservation is the newest non-superseded CONFIRMED record. A REJECTED replacement
leaves the prior confirmed reservation authoritative; an amended but unconfirmed
replacement blocks RITUAL_READY.

Example: 77.000 g is CONFIRMED, then a 80.000 g replacement DRAFT is requested.
Approval history remains valid, but RITUAL_READY is blocked until 80.000 g is
CONFIRMED. If that replacement is REJECTED, 77.000 g remains authoritative.

Pollen is measured in grams (`g`) as `BigDecimal`, scale 3, with `HALF_UP`
rounding. Need is `round3(baseRate(type) * territoryCount * launchHours *
phenomenonMultiplier)`. The initial versioned rates are SPRING 10.000,
SUMMER 12.000, AUTUMN 8.000, WINTER 15.000 g/territory-hour; the multiplier is
the product of the versioned phenomenon multipliers (default 1.000). Pollen Tree
is the sole capacity source and returns available grams and its capacity version.
Example: SPRING, two territories, 3.5 launch hours, multiplier 1.100 requires
`77.000 g` (`10 * 2 * 3.5 * 1.1`). A request above returned capacity is rejected.

Actual consumption is recorded only through Pollen Tree. At 80% and 100% of the
authoritative confirmed amount, notify the Pollen Guardian and Talent
Coordinator; above 100%, also notify the Chief Guardian. One notification per
reservation, threshold, and consumption-event id makes retries idempotent.

## Ritual, metrics, incidents, and correction actions

One SeasonPlan may have many RitualSessions, but at most one can be in progress
and a successful session is terminal for activation. A retry is allowed only
after a completed FAILED session while the plan is RITUAL_READY. A session stores
attempt number, starter, `startedAt`, `completedAt`, an immutable participant
snapshot (`fairyId`, role, confirmation timestamp), ordered events (`occurredAt`,
type, payload), result `SUCCESS` or `FAILED`, and a non-blank structured failure
code plus Russian message for FAILED. SUCCESS requires all required completion
events and command-time readiness validation; only it permits ACTIVE.

ClimateMetric values use the Metric Catalog: `temperature` in `CELSIUS`,
`humidity` in `PERCENT`, and `pollen_density` in `G_PER_M3`; a submitted unit
must equal its catalog unit. A plan records target and positive tolerance for
each metric. `deviation_score = round4(abs(value - target) / tolerance)`;
score 1.0000 is the boundary, and score >= 2.0000 is critical. Sensor import
idempotency key is `(source, sourceEventId)` and a manual entry uses its client
UUID. Example: target 20.0 C, tolerance 2.0 C, value 23.0 C gives 1.5000.

MagicIncident types are `RITUAL`, `POLLEN`, `CLIMATE`, `ASSIGNMENT`, and
`SECURITY`; severity is `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL`. Severity is set
from the selected catalog rule, with only the Chief Guardian able to override it
using a mandatory reason. An incident is operational while OPEN or
IN_PROGRESS. Any operational CRITICAL incident enables the critical operating
condition. It blocks marking ritual ready, starting/retrying a ritual, activation,
completion, report approval, and archival. Reads, incident response, task work,
and recording metrics remain available. The condition clears automatically only
after every CRITICAL incident is RESOLVED or CLOSED; reopening one restores it.

CorrectionAction statuses are `OPEN`, `IN_PROGRESS`, `DONE`, and `CANCELLED`.
Allowed transitions: OPEN to IN_PROGRESS/CANCELLED; IN_PROGRESS to OPEN/DONE/
CANCELLED; DONE and CANCELLED are terminal. Cancellation requires a reason.
An action is overdue when `now > deadline` and it is neither DONE nor CANCELLED.
The five-minute scheduler creates one escalation at the deadline and another per
24 hours overdue; deduplication key is `(actionId, escalationLevel, dueDate)`.
A qualifying deviation creates one action keyed by `(planId, metric import key,
correction rule version)`; retries must retrieve that action instead of duplicating
it.

## Reports, notification, access, and queries

Generating a report creates a DRAFT from immutable source identifiers and the
current presentation parameters. Regeneration replaces only a DRAFT; it creates
a new source snapshot and version. Chief Guardian approval seals that version as
an immutable APPROVED snapshot. HTML/PDF exports are generated only from the
approved snapshot, use its version in the filename, and an archive transition
requires that snapshot. Archived plans/reports are read-only. Example: changing
a title after approval creates a new DRAFT report and cannot alter the exported
approved content.

Notifications are durable outbox events. Recipients/channels are:

| Event | Recipients | Channel |
| --- | --- | --- |
| Submission/review result | Assigned Chief Guardian / plan creator | in-app and email |
| Pollen shortage/threshold | Pollen Guardian, Talent Coordinator, then Chief Guardian over 100% | in-app and email |
| Assignment confirmation/deadline | assigned fairy / Talent Coordinator | in-app; email for deadline |
| Critical metric/incident/escalation | Chief Guardian, Incident Manager, responsible owner | in-app and email |

Delivery is at-least-once, retrying exponentially for 24 hours. Consumers dedupe
on event id; the command transaction owns only the outbox insert.

| Operation | Fairy Planner | Chief Guardian | Talent Coordinator | Pollen Guardian |
| --- | --- | --- | --- | --- |
| Create/edit/submit own plan | yes | read | read | read |
| Verify, approve, return, complete/archive, approve report | read | yes | read | read |
| Tasks and assignments | read | read | yes | read |
| Reservation request/confirm/reject/amend | read | read | read | yes |
| Ritual start/result | read | read | command service | read |
| Incidents/correction actions | read | yes | response task work | read |
| Audit/read reports | own plan | all visible | assigned/visible | reservations/visible |

Server authorization applies this matrix and resource visibility. “Filter by
user” means: SeasonPlan creator for plan lists; task assignee or creator for
task lists; incident opener, assignee, or responsible owner for incident lists;
audit actor for audit lists; report creator or approver for report lists. Search
is case-insensitive substring matching on identifier/title/type/territory as
applicable; status and territory filters are exact normalized values. Results are
paginated with a stable `createdAt, id` sort.

Seasonal-window conflict scope is territory-specific: a normalized territory may
not have overlapping `[start,end)` launch windows in another applicable
(`SUBMITTED`, `VERIFIED`, `APPROVED`, `RITUAL_READY`, `ACTIVE`) plan. Different
territories do not conflict. A DRAFT can be saved but submission reports each
conflicting plan/window; completed and archived plans are historical and do not
block.

MagicTask statuses are `TODO`, `READY`, `BLOCKED`, `COMPLETED`, and `CANCELLED`.
Only TODO/READY/BLOCKED unstarted tasks may be replanned; a completed task is
immutable and cancellation requires a reason. A group command validates every
selected task before changing any: invalid/unauthorized/stale input produces no
changes and returns per-item errors. This all-or-nothing rule makes retries safe.

KPIs: pollen variance is `actual - confirmed` grams; consumption ratio is
`actual / confirmed`; incident count is by severity; mean deviation is the mean
of all scores; critical-deviation count is scores >= 2; ritual success rate is
successful completed sessions / completed sessions. Historical comparison uses
only APPROVED reports of the same season type and same catalog version from the
previous three calendar years, with unavailable data explicitly marked. Audit
integrity verification recalculates an append-only SHA-256 hash chain over the
canonical immutable audit fields and reports the first mismatch; it writes only
a separate verification result/audit event and never changes historical rows.

## Performance and traceability

The repeatable performance profile is 50 concurrent authenticated users for 30
minutes, 100 command/events per minute, and 1,000 metrics per season across 100
plans. Normal network is 100 ms round-trip, 10 Mbit/s, 0.1% packet loss. After a
five-minute warm-up, p95 main-screen load must be <=3 s, p95 standard/save
operation <=5 s, error rate <1%, and p99 is recorded. “Without degradation”
means p95 with 1,000 metrics is no more than 10% above the documented 100-metric
baseline under the same profile.

| Requirements | Owning use case or cross-cutting task |
| --- | --- |
| FR-1–FR-6 | UC-1 / ST-05 |
| FR-7–FR-13 | ST-00, ST-02, ST-03, UC-4 ST-09, UC-5 ST-14/ST-15 |
| FR-14–FR-19 | ST-04, UC-2 ST-06, UC-3 ST-07 |
| FR-20–FR-23 | UC-4 pollen consumption / ST-10; report inputs ST-14/ST-15 |
| FR-24–FR-34 | UC-3 / ST-07 |
| FR-35–FR-40 | UC-3 ST-07; RitualSession ST-09 |
| FR-41–FR-47 | UC-4 RitualSession / ST-09 |
| FR-48–FR-53 | UC-4 ClimateMetric / ST-11 |
| FR-54–FR-58 | UC-4 CorrectionAction / ST-12 |
| FR-59–FR-68 | UC-4 incidents/operating condition / ST-13 |
| FR-69–FR-74 | UC-5 completion/report draft / ST-14 and ST-15 |
| FR-75–FR-77 | UC-5 report approval/export/archive / ST-15 |
| FR-78–FR-81 | ST-03 and each critical command slice |
| FR-82–FR-85 | UC-1 ST-05, ST-08, Phase D |
| FR-86–FR-89 | ST-03, UC-1 ST-05, UC-3 ST-07 |
| FR-90 | UC-4 ClimateMetric / ST-11 |
| FR-91 | UC-4 incidents / ST-13 |
