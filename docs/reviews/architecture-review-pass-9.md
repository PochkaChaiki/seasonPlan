# Architecture/code review — pass 9

## Review

**Merge verdict: OK with notes — ACCEPT for the reviewed safety scope.**

**Severe inventory: zero HIGH and zero CRITICAL findings.**

AR-03 and AR-04 are closed for the current integrated implementation. This is an independent source-review decision. It does not establish full feature completion or production readiness.

No issues found.

This is the single authorized review for this continuation. No fix/review loop was performed.

Path abbreviations:

- **J/** = `backend/src/main/java/com/seasonplan/`
- **T/** = `backend/src/test/java/com/seasonplan/`
- **M/** = `backend/src/main/resources/db/changelog/changes/`

## Correct: AR-03 — revision-bound resource evidence

**Disposition: CLOSED.**

The former failure sequence was: confirm resources, edit material parameters, then approve or launch using the old confirmation. The current implementation interrupts that sequence.

### Material identity and history

- New drafts start at material revision 1: `J/season/domain/SeasonPlan.java:36–60`.
- Draft edits compare canonical material values and advance the revision once when values change: `J/season/domain/SeasonPlan.java:227–240`.
- Canonical snapshots copy collections and sort territory/window inputs: `J/season/domain/MaterialParameters.java:5–21`.
- History rejects material changes within an existing revision and requires sequential revision advancement: `J/season/domain/MaterialRevision.java:9–24`.
- Persistence saves history with the plan and checks history consistency: `J/season/adapter/out/persistence/JdbcSeasonPlanRepository.java:43–54,143–148`.
- Exact window values survive PostgreSQL timestamp projection; a different projection fails closed: the same repository, `:179–187`.
- Migration 016 records existing data as revision 0, explicitly labelled `LEGACY_BASELINE`. It does not invent earlier history. It also makes history append-only: `M/016-season-material-revisions.sql:3–49`.

### Funding cannot silently move to another revision

Reservation preparation captures the current material revision before provider I/O. Decisions require an existing binding for that revision:

`J/pollen/application/PollenService.java:65–94`.

A provider GET cannot create a binding. Request application creates a binding from recorded request evidence:

`J/pollen/application/PollenService.java:165–184`.

The database also checks that a binding refers to matching accepted request evidence:

`M/017-pollen-operation-evidence.sql:60–79`.

Approval compares the reservation binding with the current revision and rejects unresolved evidence:

`J/season/domain/SeasonPlanPolicy.java:88–105`.

Readiness independently requires current, resolved, authoritative confirmation:

`J/ritual/domain/RitualReadinessPolicy.java:23–36`.

**Causal result:** after a material edit, old funding remains historical evidence, not current authorization. A delayed result is retained against its captured revision and cannot fund the edited plan.

### Concrete regression evidence inspected

- Full old-confirmation/edit/reapproval sequence, including fresh confirmation restoring eligibility:  
  `T/lifecycle/service/PollenFundingRevisionTest.java:11–39`.
- PostgreSQL reload, old-funding rejection and fresh-funding acceptance:  
  `T/lifecycle/infrastructure/jdbc/PollenJournalPersistenceTest.java:224–256`.
- Delayed obsolete receipt retention: the same test file, `:184–206`.
- History rollback, legacy baseline and fractional-window preservation:  
  `T/lifecycle/infrastructure/jdbc/MaterialRevisionPersistenceTest.java:40–118`.

These tests support the source trace; their names alone are not the basis for closure.

## Correct: AR-04 — durable remote-command safety

**Disposition: CLOSED for reservation request, confirmation, rejection and the implemented consumption path.**

### Reservation commands

`J/pollen/application/PollenService.java` now separates:

1. **Committed intent under the season lock:** `:73–94`.
2. **Provider mutation after that transaction returns:** `:97–121`.
3. **Independent receipt persistence:** `:145–152`.
4. **Short local binding/audit/completion transaction:** `:165–180`.

Existing claims enter reconciliation rather than another provider mutation. Missing or failed reconciliation remains pending:

`:123–142,158–164`.

Request correlation uses a fresh provider key, separate from the client retry key. The implicit legacy key is stable for its captured inputs:

`:77–81`.

**Causal result:** provider acceptance followed by response loss or local audit failure does not cause blind redispatch. A saved receipt permits local-only completion. Without authoritative evidence, the operation remains blocked.

Inspected PostgreSQL tests verify committed intent visibility, an independent connection acquiring the season lock during provider mutation, fresh-service recovery, concurrent single dispatch and actual audit rollback:

`T/lifecycle/infrastructure/jdbc/PollenJournalPersistenceTest.java:60–128,145–182`.

### Consumption commands

Consumption uses separate immutable intent and receipt records:

`J/pollen/domain/ConsumptionIntent.java:9–68`.

The service preserves `(planId, clientEventId)` identity, normalizes the amount, rejects ambient transactions and resolves a non-null outbox before provider access:

`J/pollen/application/PollenService.java:194–221`.

Before a new dispatch, it commits the captured revision, actor, amount and agreement under the season lock. Only the successful new claim can dispatch:

`:222–254`.

A known receipt is persisted before local application. Local outbox writes, audit and APPLIED completion share the short transaction:

`:263–312`.

Read-only recovery does not treat post-dispatch absence as permission to spend again. Mismatched, obsolete or ineligible receipts become durable quarantine rather than successful local completion:

- `J/pollen/application/PollenService.java:275–312`.
- `J/pollen/domain/ConsumptionIntent.java:47–64`.

Unresolved reservation **or** consumption evidence blocks new resource commands and settled usage:

`J/pollen/application/PollenService.java:186–192,258–261`.

Consumption does not create a reservation funding binding.

### Concrete consumption proof inspected

`T/lifecycle/infrastructure/jdbc/PollenConsumptionPersistenceTest.java` checks:

- committed intent and unlocked provider mutation: `:91–109`;
- real audit/outbox rollback with independently retained receipt: `:110–132`;
- restart recovery, original actor and original event snapshot: `:133–147`;
- cross-kind uncertainty barriers without redispatch: `:148–161`;
- retained quarantine: `:162–173`;
- concurrent single dispatch: `:174–191`;
- forbidden evidence changes and malformed insertion: `:192–207`.

Historical amount mismatch, original audit preservation, proven denial, missing agreement/outbox and ambient-entry rejection also have concrete assertions:

`T/lifecycle/service/PollenConsumptionSafetyTest.java:43–100`.

## Retained boundary, security and domain placements

No concrete severe regression was established.

| ID | Disposition | Fresh review evidence |
|---|---|---|
| AR-01 | Remains closed | Immutable season facts copy collections; pollen facade returns immutable facts; workflows collect through public APIs. See `J/season/api/SeasonFacts.java:11–26`, `J/pollen/application/PollenOperationsFacade.java:10–29`, `J/workflows/application/ComputedRitualReadinessChecker.java:15–41`. |
| AR-08 | Remains closed | Migration 015 retains non-owning runtime restrictions. Migrations 016–018 add narrow evidence grants and mutation guards. Restricted-login tests check prohibited SQL and guard changes, including SQLSTATE `42501`: `T/lifecycle/infrastructure/jdbc/RuntimeDatabasePrivilegesTest.java:27–138`. |
| AR-17 | Remains closed | Funding decisions remain in the season domain; readiness remains in the ritual domain; consumption eligibility and warnings remain in pollen domain policy. Workflow collection does not replace those decisions. |

The complete-tree dependency guard still parses production Java, includes public APIs, rejects cycles and checks foreign implementation dependencies:

`T/architecture/CapabilityDependencyTest.java:20–76,92–166`.

HTTP reservation commands still resolve trusted actors and permissions. Reconciliation exposes safe reads/local completion, not another dispatch:

`J/workflows/adapter/in/web/LifecycleController.java:123–155`.

Pending responses include an operation reference rather than a false success:

`J/workflows/adapter/in/web/LifecycleExceptionHandler.java:22–27`.

## Retained nonsevere inventory

These are carried findings, not newly established defects.

| ID | Classification | Pass-9 review status |
|---|---|---|
| AR-10 | MEDIUM | Material history freshly reviewed and substantially improved. Canonical audit/change-detail coverage remains carried; the whole finding is not closed. |
| AR-11 | MEDIUM | Stale browser edits: not freshly re-audited. |
| AR-12 | MEDIUM | Preparation category/completion UI: not freshly re-audited. |
| AR-13 | MEDIUM | Authoritative browser reservation reload: not freshly re-audited. |
| AR-14 | MEDIUM | Role-recipient resolution: not freshly re-audited. |
| AR-15 | LOW | Unbounded listing remains visible in `J/season/adapter/out/persistence/JdbcSeasonPlanRepository.java:192–199`; scale behavior was not tested here. |

**New concrete findings:** none.  
**Fixed by this reviewer:** none; review was read-only.

## BACKLOG and integration limits

These limits are not current HIGH defects:

- The REST adapter inherits unsupported request/event lookup behavior. Unknown consumption lookup prevents the owner from sending a consumption POST. No live-provider lookup or exactly-once guarantee is established.
- Reservation authority remains a proposed provider contract: `J/pollen/adapter/out/http/RestPollenTreeClient.java:57–66`.
- Consumption recovery is a Java owner API, not an HTTP execution endpoint or browser recovery feature.
- Unknown or quarantined outcomes can remain blocked. No manual success override, release mechanism or unspecified recovery policy is claimed.
- Open business formulas and recovery policies remain open; this review does not invent replacements.

## Verification and limits

Read first: `AGENTS.md`, all of `ARCH.md`, pass 8 and the implementation checkpoint. Then inspected the current production and test seams cited above.

Only the read tool was used. No commands, builds, tests, edits, `.env` access, staging, commits or child agents were performed. Historical reports were not changed.

This was a **current-source review**, not an exact working-tree or committed-range diff review. Preservation of every earlier migration and absence of unrelated changes were not independently proved.

Supplied execution evidence, not rerun:

- Actual Java 17 clean verification: **562 tests; zero failures, errors or skips; BUILD SUCCESS**.
- Architecture, PostgreSQL, runtime and HTTP guards enabled.
- Frontend: **54 passed**, lint zero errors and one existing warning, build passed.

Process-kill recovery, live-provider behavior, deployed migration and native FreeBSD operation remain unverified.

## Final decision

**ACCEPT — the zero-HIGH/CRITICAL gate is met for the reviewed integrated safety repairs.**

AR-03 and AR-04 leave the open severe inventory. Retained MEDIUM/LOW findings and integration/deployment limits remain. This decision does not certify all features or production readiness.