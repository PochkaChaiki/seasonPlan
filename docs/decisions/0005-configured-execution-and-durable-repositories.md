# Configured execution rules and durable execution repositories

Status: Accepted. Supersedes ADR 0002/0003 statements that ritual, monitoring and incident response are core-only and have no production repository composition; reporting remains outside this decision.

## Context

The execution core previously received metric/severity/readiness contracts and ritual/metric/incident/action repositories only from test fixtures. Interface extraction and passing unit tests did not make those use cases operational.

This change implements the seven requested contracts and their supporting schema, metadata and Spring composition. Sources are the current SRS/use cases and the existing ST-00 conflict resolution. The operator explicitly selected validated versioned configuration **without business defaults**, and retained temperature/CELSIUS, humidity/PERCENT and pollen_density/G_PER_M3 rather than introducing migration index. The eight SeasonPlan lifecycle statuses remain unchanged.

## Decision

### Implement the actual dependency boundaries

| Contract | Production implementation |
| --- | --- |
| MetricRuleProvider | lifecycle.service.PinnedMetricRuleProvider |
| IncidentRuleProvider | lifecycle.config.ConfiguredIncidentRuleProvider |
| RitualReadinessChecker | lifecycle.service.ComputedRitualReadinessChecker |
| MagicIncidentRepository | lifecycle.infrastructure.jdbc.JdbcMagicIncidentRepository |
| CorrectionActionRepository | lifecycle.infrastructure.jdbc.JdbcCorrectionActionRepository |
| RitualSessionRepository | lifecycle.infrastructure.jdbc.JdbcRitualSessionRepository |
| ClimateMetricRepository | lifecycle.infrastructure.jdbc.JdbcClimateMetricRepository |

Configuration binds these to real ritual, monitoring and incident-response service beans. Core policies remain framework-independent. No new dependencies, framework/module hierarchy or generic port/shared buckets are introduced.

### Select immutable execution rules when creating a plan

Deployment must supply version labels, positive metric tolerances/response durations, owners, severity mappings and nonempty ritual event names. The source-defined ST-00 category/role table supplies the initial season requirements. Missing/invalid configuration blocks creation, not a fake-success path.

Each new plan stores an immutable execution-rule snapshot. Creation and its audit append are atomic through a creation-safe CommandTransaction operation; the existing command operation still locks an already persisted plan. Copy/rehydration preserves the snapshot, and a database trigger prevents replacing pinned rules. This shields existing plans from later deployment configuration changes.

Targets remain the plan's displayed, draft-editable values and are validated against the resolved metric catalog. Incident registration retains its existing type-only contract and uses a separately selected active incident catalog; an existing incident retains its resolved severity. That is deliberately distinguished from plan-pinned metric/readiness rules.

### Compute readiness from real facts

Preparation and ritual execution share one checker. Required categories and completed mandatory work, compatible confirmed assignments with real persisted confirmation timestamps, required roles, manual flags, current authoritative pollen and operational CRITICAL incidents all participate. READY is not COMPLETED; current time is not historical confirmation evidence.

Unverified approval conditions remain blockers until an actual evidence adapter exists. No manual flag bypasses them. Task/assignment changes participate in per-plan serialization and audit boundaries. Completion must not strand work without assignment evidence; terminal task edits produce client validation rather than an accidental server error. Ongoing permitted task work remains available during the calculated critical operating condition.

### Store execution state in PostgreSQL

Forward-only migrations 012 and 013 add canonical execution fields, ritual participant/event tables, rule snapshots, task categories and confirmation timestamps. They do not rewrite previously applied migrations, erase legacy rows or change immutable audit history.

Database protection includes global metric import/correction deduplication keys, same-plan incident/action relationships, unique ritual attempts, exclusive in-progress/successful sessions, immutable observations/original details/terminal records, sealed participants and ordered append-only events. Repository replay checks accept equivalent persisted facts and reject conflicting reuse. A failed attempt can be replayed unchanged even after a later attempt succeeds.

Measurement decimal precision is retained. Microsecond normalization is explicit in observation/confirmation handling and service import comparison, avoiding false replay conflicts after PostgreSQL storage.

### Preserve incomplete historical data rather than fabricate it

Old execution rows have a nullable canonical format marker and are retained. Missing catalog selection, categories, actual timestamps, import/deduplication keys or ritual snapshots require verified operator remediation. Affected operations fail closed rather than omitting history or assuming success. Global import/deduplication gates are explicitly documented. No automatic backfill, session cleanup, destructive migration or public remediation endpoint is supplied.

## Scope and verification

New execution HTTP routes/UI, sensors, escalation scheduling, report persistence/rendering, real Natural Calendar integration and approval-evidence verification are not delivered by these implementations. Outbox enqueueing does not prove every role-labelled recipient has received a notification. Pollen remains external and cannot be rolled back by our database transaction.

Automated coverage includes real production composition, policy/configuration checks, PostgreSQL round trips/constraints, transactional creation and command rollback, concurrent keys/attempts, immutable snapshots, and precision/replay regressions. Final run counts and commands belong to backend/CHANGELOG.md; configuration and safe rollout details belong to docs/architecture/EXECUTION_RULES_AND_PERSISTENCE.md.
