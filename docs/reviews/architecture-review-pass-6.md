# Architecture/code review — pass 6 of 6, final agreed pass

## Review

**Merge verdict: BLOCK.**

AR-08 is corrected for the supplied restricted database identity. AR-17 remains closed. **Three HIGH findings remain: AR-01, AR-03 and AR-04.** No CRITICAL finding was established.

The no-HIGH/no-CRITICAL acceptance gate is not met. **Stop implementation and report the remaining risks for user decisions. Do not start pass 7.**

### Exact current severe inventory

| ID | Classification | Current disposition |
|---|---|---|
| AR-01 | HIGH / P1 | Open: capability ownership, dependency cycles and mutable public results |
| AR-03 | HIGH / P1 | Open: resource confirmation is not bound to the material plan revision |
| AR-04 | HIGH / P1 | Open: remote mutations lack durable intent and reconciliation |
| AR-08 | Previously HIGH | Corrected for the supplied restricted runtime database identity; deployment requires DBA rollout |
| AR-17 | Previously HIGH | Remains closed for the identified business-rule placement gaps |

**Current totals: 3 HIGH, 0 established CRITICAL.** No new severe regression was established in the inspected database changes.

Path abbreviations:

- **J/** = `backend/src/main/java/com/seasonplan/`
- **T/** = `backend/src/test/java/com/seasonplan/`

## Correct: AR-08 correction independently inspected

### Runtime and migration identities are separate

- `docker-compose.yml:6–10,37–41` separates PostgreSQL initialization/Liquibase credentials from application datasource credentials.
- `backend/src/main/resources/application.yml:4–10` uses `DB_RUNTIME_*` for application requests and separate `DB_MIGRATION_*` settings for Liquibase.
- `.env.example:3–7` gives distinct owner and runtime roles and password placeholders.
- `infrastructure/postgres/010-runtime-role.sh:4–11` rejects runtime reuse of the initialization owner or the fixed grant-role name.
- The script creates an unprivileged login and grants membership in the fixed NOLOGIN role at `:16–22`.

The initialization owner is no longer the supplied request datasource identity.

### Forward migration limits runtime authority

`backend/src/main/resources/db/changelog/changes/015-runtime-database-privileges.sql`:

- Creates or validates the fixed NOLOGIN grant role at `:6–18`.
- Rejects elevated flags, parent-role membership and relation/schema/database ownership at `:9–16`.
- Revokes database TEMPORARY and CREATE at `:19–20`. This closes temporary-table shadowing as well as database-level schema creation.
- Removes PUBLIC schema CREATE and resets runtime schema/table/sequence grants at `:23–28`.
- Grants business permissions through an explicit table list at `:30–38`.
- Grants audit **SELECT and INSERT only** at `:39`. Migration-history tables are not in the business grant list.

The master changelog includes migration 015 after migration 014 at `backend/src/main/resources/db/changelog/db.changelog-master.yaml:24–27`.

This is a forward correction. I did not compare historical migration bytes or checksums; their unchanged status is supplied evidence.

### Tests exercise the actual restricted identity

Source inspection confirms meaningful PostgreSQL tests, not only configuration assertions:

- `T/lifecycle/infrastructure/jdbc/RuntimeDatabasePrivilegesTest.java:24–42` configures PostgreSQL 14, migrates as owner and connects as a separate restricted login.
- `:45–59` exercises JDBC plan save, update and reload, plus audit append/read.
- `:62–80` executes the supplied bootstrap, connects as its provisioned login and checks owner-reuse rejection.
- `:83–87` checks role flags, owner membership and relation ownership.
- `:90–123` requires SQLSTATE `42501` for 16 forbidden operations. These cover audit mutation, guard replacement, privilege escalation, permanent/temporary shadowing and migration-history mutation.
- `T/security/infrastructure/jdbc/PostgresAccountAdministrationTest.java:27–40,47–51` starts the real Spring application with the bootstrap-provisioned runtime login and separate Liquibase owner. It verifies the request identity and audit-table owner.
- `:54–83` exercises account changes and rollback under that runtime datasource.

`T/architecture/RuntimeDatabaseConfigurationTest.java:11–24` checks credential separation and the LF rule. `.gitattributes:1–2` defines LF checkout for the container bootstrap.

### Deployment limits are explicit

`docs/operations/database-runtime-identity.md`, especially **Existing database or volume**, requires DBA inspection, distinct role provisioning, controlled credential rollout and actual deployment privilege checks. It correctly states that initialization scripts do not rerun on existing volumes.

The same document, `backend/README.md` under **Transactions and persistence**, and ADR 0006 do not claim privileged-administrator immutability.

**AR-08 disposition:** close the identified supplied-runtime-identity defect. This does not certify an existing deployed database or protect compromised migration credentials. The documented DBA rollout remains required before deployment.

## Correct: AR-17 remains closed

The inspected decisions remain domain-owned:

| Decision | Current source evidence |
|---|---|
| Initial readiness checklist and review recipients | `J/season/domain/SeasonPlanPolicy.java:12–13`; called by `SeasonPlanService.java:73–75,205–210` |
| Measurement eligibility | `J/monitoring/domain/MetricReactionPolicy.java:10–14`; called by `MonitoringService.java:67` |
| Consumption eligibility | `J/pollen/domain/PollenConsumptionPolicy.java:10–13`; called by `PollenService.java:104` |
| Report generation, approval, archive and completion choice | `J/reporting/domain/ReportPolicy.java:15–20,28`; called by `SeasonReportService.java:67–68,81,87,101` |
| Ritual-start eligibility | `J/ritual/domain/RitualPolicy.java:13–15`; called by `RitualService.java:45` |
| Manual correction recipients | `J/response/domain/IncidentResponsePolicy.java:25` |

These policies make the decisions. Application code obtains facts and executes persistence and side effects.

No concrete new source finding warrants reopening AR-17. This is not a certification that every business decision in the repository has been exhaustively inspected.

The terminal checklist guard also remains present at `J/season/domain/SeasonPlan.java:278–293`, before checklist mutation. Its mutable-object exposure remains part of AR-01.

## Correct: REST/JSON remains present

`J/workflows/adapter/in/web/LifecycleController.java:26–131` retains the REST controller, HTTP mappings, JSON request bodies and response mapping. Reservation requests remain available at `:121–131`.

No REST/JSON regression was established in the inspected paths. A complete API-schema comparison was not performed.

## Findings

### AR-01 — HIGH / P1 — Actual capability boundaries remain unenforced

**Contract:** `ARCH.md` §4 requires owner-controlled repositories, small public contracts and no business-module dependency back to workflows. Package names alone do not satisfy this contract.

**Current evidence:**

- Monitoring injects response repositories and directly saves incidents and corrections:  
  `J/monitoring/application/MonitoringService.java:21–22,38–50,80–96`.
- Ritual changes and saves season state:  
  `J/ritual/application/RitualService.java:70–82`.
- Reporting changes and saves season state:  
  `J/reporting/application/SeasonReportService.java:80–108`.
- Readiness coordination depends on module-internal repositories:  
  `J/workflows/application/ComputedRitualReadinessChecker.java:4–12,17–25,33–35`.
- The season/workflows dependency cycle remains:  
  `J/season/application/SeasonPlanService.java:20–21` imports workflow authorization;  
  `J/workflows/domain/authorization/LifecycleAccessPolicy.java:4,31–50` imports and inspects season domain.
- The season/reporting dependency cycle remains:  
  `J/season/domain/SeasonPlan.java:4` imports reporting;  
  `J/reporting/domain/ReportPolicy.java:3` imports season.
- Planning public queries return mutable domain aggregates:  
  `J/season/api/SeasonPlanning.java:26–27`.  
  Unrestricted material setters and mutable checklist elements remain exposed at  
  `J/season/domain/SeasonPlan.java:124–185`.
- Work public contracts also expose domain objects:  
  `J/work/api/WorkPreparation.java:9–16`.

**Risk:** callers can depend on owner internals and bypass owner-controlled application boundaries. Rules, audit and transaction discipline depend on caller cooperation. This is an architecture ownership defect, not proof of a direct HTTP exploit or a Spring startup failure.

**Smallest safe corrective slices:**

1. Add season-owned activation/completion/archive commands and a response-owned metric-reaction command.
2. Move cross-capability coordination to workflows. Remove corresponding foreign write repositories.
3. Expose immutable public facts instead of mutable aggregates.
4. Remove the identified cycles through owner-facing contracts and immutable decision evidence.

Keep the season lock, consistent lock order and atomic state/audit/outbox transaction.

**Targeted regressions:**

- Reject foreign write-repository imports and business-module imports of workflows.
- Detect capability cycles.
- Verify public results cannot mutate owner state.
- Retain emergency-entry, ritual activation, completion and audit-failure rollback tests through the new owner commands.

### AR-03 — HIGH / P1 — Resource confirmation is not bound to the material revision

**Contract:** `ARCH.md` §§5–7 requires revision-bound resource evidence and current-revision checks before approval and launch.

**Current evidence:**

- Material draft fields change without advancing a revision or fingerprint:  
  `J/season/domain/SeasonPlan.java:220–229`.
- Approval loads provider confirmation, then applies a decision based on reservation status and season identity:  
  `J/season/application/SeasonPlanService.java:139–151`;  
  `J/season/domain/SeasonPlanPolicy.java:88–95`.
- Readiness checks season identity, reservation identity and confirmation status, not the material revision:  
  `J/ritual/domain/RitualReadinessPolicy.java:24–33`.

**Static reproduction:**

1. Confirm resources for a submitted plan.
2. Return the plan to draft.
3. Change material parameters.
4. Resubmit and verify.
5. The old confirmation still satisfies the inspected resource predicates.

This uses existing commands; it is not a missing-future-feature finding.

**Smallest safe fix:** persist a material revision or canonical fingerprint. Bind reservation intent and accepted confirmation to that value. Domain policies must require the current binding at approval and launch. An obsolete result must not fund the current plan.

**Targeted regressions:**

- Old confirmation fails after material edits.
- Current-revision confirmation restores eligibility.
- A delayed obsolete confirmation cannot fund the edited plan.
- The checks survive persistence reload.

### AR-04 — HIGH / P1 — Remote mutations lack durable intent and reconciliation

**Contract:** `ARCH.md` §7.2 requires durable local intent before remote mutation, stable operation identity, unknown-outcome handling and reconciliation.

**Current evidence:**

- Reservation request calls the provider directly without saving local intent:  
  `J/pollen/application/PollenService.java:59–64`.
- Confirmation and rejection call the provider before local audit completion:  
  `:67–77,114–119`.
- Consumption calls the provider inside the local season command transaction:  
  `:98–110`.
- The REST controller generates a new request ID when the caller omits one:  
  `J/workflows/adapter/in/web/LifecycleController.java:128–130`.

**Failure scenario:** the provider accepts an operation, then the response is lost, the process stops or local audit persistence fails. A local rollback cannot undo the accepted remote mutation. Without durable intent and recovery state, the application cannot reliably identify and reconcile that outcome. Provider replay guards alone do not recover a lost operation identity.

Reservation request/decision paths are implemented REST paths. Consumption is also an implemented core path when its outbox is configured.

**Smallest safe fix:**

1. Commit stable operation identity and intent locally.
2. Perform remote I/O outside the long season transaction.
3. Persist pending/unknown outcomes.
4. Reconcile before unsafe replay.
5. Apply accepted results in a short transaction, with current material-revision validation.

Keep provider calls behind the existing port. Do not assume exactly-once remote execution.

**Targeted regressions:**

- Provider accepts, then times out.
- Restart occurs before result persistence.
- Local audit fails after provider acceptance.
- Retry/reconciliation retains the original operation key.
- Obsolete results cannot fund the current revision.

Cover reservation creation, confirmation/rejection and consumption.

## Retained non-severe inventory

These pass-5 carry-forward entries retain their IDs and classifications. They were **not freshly re-audited or closed** in this focused final pass.

| ID | Classification | Subject |
|---|---|---|
| AR-10 | MEDIUM | Immutable material history and canonical audit change evidence |
| AR-11 | MEDIUM | Stale browser-edit detection |
| AR-12 | MEDIUM | Preparation category/completion UI |
| AR-13 | MEDIUM | Authoritative reservation reload |
| AR-14 | MEDIUM | Role-recipient resolution to account inboxes |
| AR-15 | LOW | Unbounded aggregate listing |

No IDs were reassigned. Missing future features, unspecified formulas and open business decisions were not promoted into new defects.

## Verification and limits

### Independent static inspection

I read `AGENTS.md`, all of `ARCH.md`, the pass-5 report, and the current source, configuration, migration, bootstrap, documentation and test seams cited above.

Only the read tool was used. No files were changed. No commands, builds, tests, `.env` reads, staging, commits or child agents were run.

This is current-source inspection, not an exact diff or committed-range review. Historical checksum preservation and the full absence of unrelated changes were not independently established.

### Supplied execution evidence

The task supplies:

- Actual Java 17 clean verification: **425 tests, 0 failures, 0 errors, 0 skips**, including PostgreSQL.
- Observed RED failures for credential mismatch, missing business privileges and permitted permanent/temporary shadowing.
- Then **23 focused tests passed**.
- After the clean run, only test whitespace and the LF assertion/`.gitattributes` changed; the focused configuration test then passed.
- Earlier frontend verification: **43 tests**, lint **0 errors / 1 existing warning**, successful build.

I inspected the relevant test source, but did not independently execute these results. No deployed database, native FreeBSD rollout or live-provider recovery was verified.

## Final decision

**BLOCK — AR-01, AR-03 and AR-04 remain HIGH.**

AR-08 is corrected for the supplied restricted database identity. AR-17 remains closed. Neither correction removes the remaining capability-ownership and resource-consistency risks.

**This is the sixth and final agreed pass. Stop implementation, present these three HIGH risks and the retained non-severe inventory to the user, and obtain user decisions before further implementation. No pass 7.**