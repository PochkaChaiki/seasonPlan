# Architecture/code review — pass 7 of 12

## Review

**Merge verdict: BLOCK.**

**Three HIGH findings remain: AR-01, AR-03 and AR-04. No CRITICAL finding was established.** The zero-HIGH/CRITICAL acceptance gate is not met.

The new owner services reduce AR-01. They do **not** close it. No new severe regression was established in the inspected lifecycle evidence, metric replay, transaction or authorization paths.

Passes 7–12 are now authorized. The historical pass-6 stop instruction is superseded.

Path abbreviations:

- **J/** = `backend/src/main/java/com/seasonplan/`
- **T/** = `backend/src/test/java/com/seasonplan/`

## Correct: bounded ownership improvements

### Season lifecycle writes now have an owner

`J/season/application/SeasonLifecycleService.java:21–56` loads the season, applies its domain transition, saves it and appends its audit record inside `CommandTransaction`.

Inspected callers now request owner commands:

- Ritual activation: `J/ritual/application/RitualService.java:69–84`.
- Report completion and archive: `J/reporting/application/SeasonReportService.java:80–108`.
- Incident emergency entry: `J/response/application/IncidentResponseService.java:48–70,105–115`.

Season transition guards use scalar evidence rather than ritual/reporting aggregates:

- `J/season/domain/SeasonPlan.java:295–302`: matching season and successful ritual.
- `:322–343`: permitted state, matching season, approved report and no critical snapshot incidents.

The evidence contract is trusted internal coordination, not an HTTP authorization credential. Current callers retain their authorization and source-validation checks. No evidence of a new HTTP bypass was established.

### Metric response writes now belong to response

`J/monitoring/application/MonitoringService.java:54–69` validates observations, checks replay, obtains a domain reaction and delegates response effects. It saves only the metric and metric audit.

`J/response/application/MetricReactionService.java:33–62` owns incident/correction persistence, response audit, notification requests and emergency delegation.

Retry protection is concrete:

- Stable incident identity derives from season and metric IDs at `:37–38`.
- An existing incident skips repeated incident effects at `:39–48`.
- Correction lookup uses the existing correction key at `:51–58`.
- `J/response/api/MetricReactions.java:12–14` copies alert recipients into an immutable set.

Monitoring still makes measurement-response decisions in `J/monitoring/domain/MetricReactionPolicy.java:18–33`; the owner service executes them. No new formulas or thresholds were introduced in this inspected seam.

### Nested transactions retain atomicity

`J/season/adapter/out/persistence/JdbcCommandTransaction.java:18–35` uses a default `TransactionTemplate` and locks the season row before executing commands. Nested owner calls therefore join the existing REQUIRED transaction.

`J/bootstrap/LifecycleConfiguration.java:91–121` wires the lifecycle, response and monitoring services to the shared transaction contract.

Relevant test source supports these guarantees:

- `T/lifecycle/infrastructure/jdbc/PostgresExecutionRepositoriesTest.java:210–257`: outer rollback across nested lifecycle commands and audit failure for all four transitions.
- `:261–274`: direct response-owner replay preserves incident, correction, audit and notification counts.
- `T/lifecycle/infrastructure/jdbc/PostgresLifecyclePersistenceTest.java:726–768`: four metric-path audit failure points, full rollback, retry and replay.
- `T/lifecycle/domain/SeasonLifecycleEvidenceTest.java:14–47`: wrong-season, unsuccessful, unapproved and critical evidence rejection.

These are source-inspected tests. Their execution results are supplied evidence.

### Authorization decisions now belong to identity

`J/identity/domain/LifecycleAuthorizationPolicy.java:13–26` contains the explicit permission matrix. Owner checks and multi-role selection are at `:33–60`.

`J/identity/api/LifecycleAccessPolicy.java:6–25` exposes scalar ownership facts and delegates decisions to identity. Inspected business services import this public contract instead of workflow authorization.

The inspected callers still enforce permission checks before owner checks, including:

- Draft update/submission: `J/season/application/SeasonPlanService.java:84–115`.
- Report generation: `J/reporting/application/SeasonReportService.java:66–72`.
- Correction changes: `J/response/application/IncidentResponseService.java:87–95`.

Exact historical matrix/order equivalence was not independently established by a before/after diff.

## Findings

### AR-01 — HIGH — Capability boundaries remain incomplete

**Contract:** `ARCH.md` §4 requires small public contracts, owner-controlled repositories and cross-module coordination through those contracts.

**Current evidence:**

- Monitoring still reads season through its internal repository and `ServiceSupport`:  
  `J/monitoring/application/MonitoringService.java:8,15,30,55`.
- Readiness coordination still consumes internal season/work/response repositories and domain aggregates:  
  `J/workflows/application/ComputedRitualReadinessChecker.java:4–12,17–25,28–42`.
- Reporting still loads foreign repositories and aggregates directly:  
  `J/reporting/application/SeasonReportService.java:3–16,23–30,43–49`.
- Planning public commands and queries expose mutable `SeasonPlan` aggregates:  
  `J/season/api/SeasonPlanning.java:12–27`.
- Public material setters and mutable checklist elements remain:  
  `J/season/domain/SeasonPlan.java:124–185`.
- Work public contracts expose domain objects:  
  `J/work/api/WorkPreparation.java:9–16`.
- A reference/season dependency cycle remains: season imports the reference calendar port at  
  `J/season/application/SeasonPlanService.java:25`; that port accepts `SeasonPlan` at  
  `J/reference/application/port/NaturalCalendarClient.java:3–8`.
- Season/ritual coupling remains bidirectional:  
  `J/season/application/SeasonPlanService.java:7,49`;  
  `J/ritual/domain/RitualReadinessPolicy.java:4,17–25`.

**Risk:** owner isolation still depends on caller discipline. Mutable public models and internal-repository dependencies allow coordination to bypass stable owner contracts. This is not evidence of a direct HTTP exploit.

**Smallest safe next slices:**

1. Introduce immutable season, work and response facts for readiness.
2. Move readiness fact collection behind public owner query contracts.
3. Replace mutable planning/work public results incrementally.
4. Remove the remaining dependency cycles through scalar inputs and immutable evidence.

Retain current REST/JSON models at the adapter boundary, actor attribution, row locking and atomic audit/outbox behavior.

**Tests:** reject foreign repository imports, detect capability cycles, prove public facts cannot mutate owner state, and retain launch/emergency/completion rollback tests.

### AR-03 — HIGH — Resource confirmation is not bound to the material plan revision

**Contract:** `ARCH.md` §§5–7 requires revision-bound resource evidence at approval and launch.

**Current evidence:**

- Material draft changes do not advance a revision or fingerprint:  
  `J/season/domain/SeasonPlan.java:220–229`.
- Approval retrieves resource evidence without a material binding:  
  `J/season/application/SeasonPlanService.java:139–153`.
- Resource acceptance checks confirmation status and season identity:  
  `J/season/domain/SeasonPlanPolicy.java:88–95`.
- Readiness checks reservation/season identity and confirmation status, not material revision:  
  `J/ritual/domain/RitualReadinessPolicy.java:24–33`.

**Static reproduction:** confirm resources, return the submitted/verified plan to draft, change material fields, resubmit, verify and approve. The old confirmation still satisfies the inspected resource predicates.

**Smallest safe next slice:** persist a material revision or canonical fingerprint; bind reservation intent and accepted confirmation to it; require matching current evidence at approval and launch.

**Tests:** reject old confirmation after edits; accept a new matching confirmation; reject delayed obsolete results; verify bindings after persistence reload.

### AR-04 — HIGH — Remote mutations lack durable intent and reconciliation

**Contract:** `ARCH.md` §7.2 requires durable intent, stable operation identity, unknown-outcome handling and reconciliation.

**Current evidence in `J/pollen/application/PollenService.java`:**

- Reservation request calls the provider without first persisting local intent: `:59–64`.
- Confirmation/rejection mutate the provider before local audit completion: `:67–77`.
- Consumption calls the provider inside the season transaction: `:98–110`.
- Decision audit is persisted after the provider result: `:114–119`.

**Failure scenario:** the provider accepts a mutation, then the response is lost or local audit fails. PostgreSQL rollback cannot undo the remote mutation. The inspected path has no durable recovery record for that uncertain outcome.

**Smallest safe next slice:**

1. Commit stable operation identity and intent locally.
2. Perform provider I/O outside the long season transaction.
3. Persist pending/unknown outcomes.
4. Reconcile before unsafe replay.
5. Apply accepted results in a short transaction with current material-revision validation.

Keep the existing provider port. Do not assume exactly-once remote execution.

**Tests:** provider acceptance followed by timeout; restart before result persistence; audit failure after acceptance; stable-key recovery; obsolete-result rejection. Cover reservation request, confirmation/rejection and consumption.

## Retained inventory

| ID | Classification | Disposition |
|---|---|---|
| AR-01 | HIGH | Reduced, still open |
| AR-03 | HIGH | Open |
| AR-04 | HIGH | Open |
| AR-08 | Previously HIGH | Remains closed for the supplied restricted runtime identity |
| AR-17 | Previously HIGH | Remains closed for the reviewed policy-placement gaps |
| AR-10 | MEDIUM | Material history and canonical audit evidence; carried forward |
| AR-11 | MEDIUM | Stale browser-edit detection; carried forward |
| AR-12 | MEDIUM | Preparation category/completion UI; carried forward |
| AR-13 | MEDIUM | Authoritative reservation reload; carried forward |
| AR-14 | MEDIUM | Role-recipient resolution; carried forward |
| AR-15 | LOW | Unbounded aggregate listing; carried forward |

The non-severe entries were not freshly re-audited. No concrete current evidence warrants reopening AR-08 or AR-17.

## Verification and limits

**Independent work:** read-only current-source inspection of `AGENTS.md`, `ARCH.md`, pass 6, both supplied worker artifacts, and the source/test seams cited above.

Only the read tool was used. No commands, tests, builds, edits, `.env` reads, staging, commits or child agents were run. This was not an exact diff or committed-range review; complete absence of unrelated changes was not established.

**Supplied execution evidence:**

- Identity RED: two architecture-test failures; focused GREEN: 78 tests.
- Actual Java 17 clean verification: **460 tests, zero failures/errors/skips, BUILD SUCCESS**, including PostgreSQL and restricted-runtime tests.
- After tests, Surefire logged a forced-fork shutdown warning and PostgreSQL connection-refused output. **Teardown was not warning-free.**
- Frontend: **43 tests**, lint **zero errors / one existing warning**, build passed.

No deployed database, live-provider recovery or native FreeBSD deployment was verified.

## Final decision

**BLOCK — AR-01, AR-03 and AR-04 remain HIGH.**

The inspected owner slices are sound bounded improvements. They do not satisfy the whole-architecture acceptance gate. Further work remains within the authorized pass-12 limit and under the parent’s acceptance and implementation authority.