# Architecture/code review — pass 8 of 12

## Review

**Merge verdict: BLOCK.**

**Severe inventory: two HIGH findings remain, AR-03 and AR-04. No CRITICAL finding was established.**

**AR-01 is closed for the reviewed capability ownership, immutable public-contract and dependency-cycle boundary.** This is an independent source-review disposition, not acceptance inherited from the parent. The overall zero-HIGH/CRITICAL gate is still not met.

This report completes **pass 8**, including its native retry. It is not pass 9.

Path abbreviations:

- **J/** = `backend/src/main/java/com/seasonplan/`
- **T/** = `backend/src/test/java/com/seasonplan/`

## Correct: AR-01 boundary repairs

### Owner queries replace foreign repository access

The inspected query implementations use their own repositories:

- `J/season/application/SeasonQueryService.java:12–19` loads seasons and maps them to public facts.
- `J/work/application/WorkQueryService.java:12–23` maps tasks and assignments to immutable facts.
- `J/response/application/ResponseQueryService.java:10–18` returns frozen lists of immutable incident/action records.

The significant consumers now use public owner contracts:

- Monitoring: `J/monitoring/application/MonitoringService.java:29–40,54–69`.
- Readiness: `J/workflows/application/ComputedRitualReadinessChecker.java:15–41`.
- Ritual: `J/ritual/application/RitualService.java:27–38,41–84`.
- Reporting: `J/reporting/application/SeasonReportService.java:41–60,140–144`.

These are real owner boundaries, not renamed foreign repositories.

Regression guards cover the broader production tree:

- `T/architecture/OwnerQueryBoundaryTest.java:10–29` rejects foreign repository references, including fully qualified references.
- `T/architecture/SeasonQueryBoundaryTest.java:8–19` rejects foreign season repository and `ServiceSupport` access.

### Public commands no longer expose mutable aggregates

`J/season/api/SeasonPlanning.java:10–28` accepts immutable drafts and returns facts. Its facade maps inputs into owner objects and snapshots returned command objects directly:

- `J/season/application/SeasonPlanningFacade.java:13–27`.
- `J/work/application/WorkPreparationFacade.java:13–19`.
- `J/pollen/application/PollenOperationsFacade.java:13–24`.

Checklist completion also uses the command result directly:

`J/workflows/adapter/in/web/LifecycleController.java:115–122`.

`J/season/api/SeasonFacts.java:11–26` copies material collections and represents checklist entries as immutable records. The inspected supported foreign values are not mutable aggregate escapes:

- `J/reference/domain/ExecutionRuleSnapshot.java:11–39` freezes nested collections.
- `J/response/domain/MagicIncident.java:7–39` is an immutable record; changes create new records.

Source-inspected regressions include:

- Recursive aggregate-exposure and HTTP-import checks: `T/architecture/ImmutableCommandContractTest.java:10–43`.
- Input alias, detached result, invalid-value and no-post-command-query tests: `T/lifecycle/service/CommandFacadeIsolationTest.java:11–84`.

The HTTP controller still resolves trusted actors and permissions before commands. Inspected routes remain unchanged. Exact historical wire equivalence is supported by supplied tests, not by an independently inspected before/after diff.

### Cycle removal retains domain decisions

The new ports remove the identified reverse capability dependencies without moving policy into workflows:

- `J/reference/api/CalendarRequest.java:7–14` contains immutable calendar inputs, not a season aggregate.
- `J/season/application/SeasonPlanService.java:98–101` maps season data into that request.
- `J/workflows/application/PollenResourceEvidenceSource.java:10–18` only loads/maps pollen evidence.
- `J/season/domain/SeasonPlanPolicy.java:81–104` retains resource acceptance, rejection and readiness-error decisions.
- `J/workflows/application/ComputedRitualReadinessChecker.java:25–41` collects facts and calls the public ritual evaluator.
- `J/ritual/domain/RitualReadinessPolicy.java:17–67` directly implements that evaluator and retains readiness rules.

Bootstrap wires the minimal season ports and the direct ritual evaluator at:

`J/bootstrap/LifecycleConfiguration.java:135–150`.

Configuration validation was not reduced to obtain this separation. `J/bootstrap/DeploymentExecutionRules.java:15–22` validates all incident types before returning the reference execution snapshot.

### Architecture proof is materially stronger

`T/architecture/CapabilityDependencyTest.java`:

- Builds the capability graph from all production Java sources, including public API sources: `:20–30,67–76`.
- Parses member selections, including fully qualified references, rather than treating comments or strings as dependencies: `:92–106`.
- Includes the root application as bootstrap: `:109–118`.
- Checks complete-graph cycles: `:120–142`.
- Rejects foreign implementation/policy dependencies while permitting public contracts and their supported record/enum values: `:33–56,145–166`.
- Includes synthetic parser and cycle regressions: `:59–65`.

This is useful enforcement of the reviewed source boundary. It is not a proof of every possible runtime dependency.

### Local consistency remains intact

`J/season/adapter/out/persistence/JdbcCommandTransaction.java:18–35` retains the shared transaction template and season row lock. Work commands retain season locking, participant locking, domain validation and audit writes:

`J/work/application/MagicTaskService.java:29–79`.

Ritual completion retains its outer transaction and delegates activation to the season owner:

`J/ritual/application/RitualService.java:67–84`.

Inspected PostgreSQL regressions cover outer-command rollback and nested lifecycle audit failures:

`T/lifecycle/infrastructure/jdbc/PostgresExecutionRepositoriesTest.java:255–299`.

**AR-01 disposition: CLOSED.** No concrete residual HIGH ownership, mutable-contract or rule-placement defect was established in the inspected boundary. No new severe regression was established.

## Findings

### AR-03 — HIGH / P1 — Resource confirmation is not bound to material plan revision

**Contract:** `ARCH.md` §§5–7 requires current, revision-bound funding evidence.

**Current evidence:**

- Material draft edits replace fields without advancing a material revision or fingerprint:  
  `J/season/domain/SeasonPlan.java:219–228`.
- Approval obtains latest and confirmed resource evidence without a material binding:  
  `J/season/application/SeasonPlanService.java:143–160`.
- The resource predicate checks confirmation status and season identity, not material identity:  
  `J/season/domain/SeasonPlanPolicy.java:88–98`.
- Readiness checks reservation status and identity, not material revision:  
  `J/ritual/domain/RitualReadinessPolicy.java:23–35`.

**Static failure sequence:** confirm resources; return the plan to draft; change material fields; resubmit, verify and approve. The old confirmation still satisfies the inspected funding predicates.

**Smallest safe fix:** persist a material revision or canonical fingerprint, bind reservation intent/results to it, and require a current match at approval and launch.

**Required regressions:** reject old confirmation after material edits; accept matching new confirmation; reject delayed obsolete results; preserve the binding after persistence reload.

**Disposition:** unchanged and open.

### AR-04 — HIGH / P1 — Remote mutations lack durable intent and reconciliation

**Contract:** `ARCH.md` §7.2 requires durable intent, stable operation identity, unknown-outcome handling and reconciliation.

**Current evidence in `J/pollen/application/PollenService.java`:**

- `:58–63`: reservation request calls the provider without first saving durable local intent.
- `:66–76`: confirmation/rejection mutate the provider before local audit completion.
- `:98–111`: consumption performs provider I/O inside the season transaction.
- `:115–119`: decision audit follows the provider result.

**Failure sequence:** the provider accepts the mutation, then its response is lost or local persistence fails. PostgreSQL rollback cannot undo that remote mutation. The inspected command path has no durable recovery state for the uncertain outcome.

**Smallest safe fix:**

1. Commit stable operation identity and intent locally.
2. Perform remote I/O outside the long season transaction.
3. Persist pending/unknown outcomes.
4. Reconcile before unsafe replay.
5. Apply accepted results in a short transaction with current material-binding checks.

Do not assume exactly-once provider execution.

**Required regressions:** acceptance followed by timeout; restart before result persistence; audit failure after acceptance; stable-key recovery; obsolete-result rejection. Cover request, confirmation/rejection and consumption.

**Disposition:** unchanged and open.

## Retained inventory

| ID | Classification | Pass-8 disposition |
|---|---|---|
| AR-01 | Previously HIGH | Closed for the reviewed ownership/API/DAG boundary |
| AR-03 | HIGH | Open |
| AR-04 | HIGH | Open |
| AR-08 | Previously HIGH | Remains closed; no concrete reopening evidence |
| AR-17 | Previously HIGH | Remains closed; no concrete reopening evidence |
| AR-10 | MEDIUM | Material history and canonical audit evidence; carried forward |
| AR-11 | MEDIUM | Stale browser-edit detection; carried forward |
| AR-12 | MEDIUM | Preparation category/completion UI; carried forward |
| AR-13 | MEDIUM | Authoritative reservation reload; carried forward |
| AR-14 | MEDIUM | Role-recipient resolution; carried forward |
| AR-15 | LOW | Unbounded aggregate listing; carried forward |

The nonsevere entries were not freshly re-audited. Existing BACKLOG and reporting/core-only composition limits are not promoted into new defects or claimed complete.

## Verification and limits

**Independent work:** read-only inspection of `AGENTS.md`, all of `ARCH.md`, pass 7, supplied worker artifacts, and the current production/test seams cited above.

Only the **read tool** was used. No commands, tests, builds, edits, `.env` reads, staging, commits or child agents were run. No fixes were applied.

This was a current-source review, not an exact diff or committed-range review. Complete absence of unrelated changes was not independently established.

**Supplied execution evidence, not independently rerun:**

- Latest architecture RED: two real failing tests; focused GREEN: **87 tests**.
- Actual Java 17 clean verification: **504 tests, zero failures/errors/skips, BUILD SUCCESS**, including PostgreSQL, restricted-runtime and executable-JAR checks.
- Clean test-context shutdown reported; the pass-7 forced-shutdown warning is superseded by that supplied result.
- Frontend: **43 tests passed**, lint **zero errors / one existing warning**, build passed.
- Parent reports no staging and clean `git diff --check`.

No live-provider recovery, deployed database or native FreeBSD runtime was verified.

## Final decision

**BLOCK — AR-03 and AR-04 remain HIGH.**

AR-01 can leave the open severe inventory. Overall acceptance and implementation authority remain with the parent.