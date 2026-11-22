# ADR 0006: Capability modules and domain-owned decisions

Status: implementation resumed. After review pass 6 was BLOCKED, the user authorized up to six additional review/fix passes, numbered 7–12. Acceptance still requires no HIGH or CRITICAL findings.

The [final review](../reviews/architecture-review-pass-6.md) retains three HIGH findings: AR-01 (ownership/cycles/mutable public results), AR-03 (revision-bound resource evidence) and AR-04 (durable remote intent/reconciliation). AR-08 is corrected for the supplied runtime identity; AR-17 remains closed for the reviewed domain-rule gaps.

## Decision

Follow `ARCH.md` and the user's explicit requirement for business logic in domain. Keep one Spring Boot application, one Maven backend module and one PostgreSQL database. Organize production Java code by capability, not under the former global `lifecycle` and `security` layers.

Capabilities are `season`, `pollen`, `work`, `ritual`, `monitoring`, `response`, `reporting`, `reference`, `identity`, `audit` and `notification`. `workflows` coordinates cross-capability work. `bootstrap` composes implementations. `shared` must remain a small technical/value-type foundation.

Within each capability, use domain, application/ports, public API and adapters as needed. A use-case/application method must coordinate actor context, transactions, fact loading, domain evaluation, persistence and side effects. Required fields, allowed changes, resource rules, deadlines, readiness and completion decisions belong in entities or pure domain policies.

This supersedes the package-placement choices in ADR 0002–0004 where those choices conflict with `ARCH.md`. It does not remove their behavior, authentication, ownership or transaction requirements.

## Implemented refactor

- Production types were moved to capability packages; no old namespace forwarding wrappers remain.
- Framework, persistence, HTTP and mail code are adapters; Spring wiring is under bootstrap.
- Controllers use public planning, work, pollen and actor contracts. Audit is accessed through its public contract.
- Planning, scheduling, readiness, ritual completion, metric, reaction, reporting, account and resource decisions have explicit domain policies.
- The existing seasonal requirement table is now a labelled educational domain catalog, not configuration-adapter policy. No new seasonal formulas or rules were invented.
- Reservation transition and exact-replay invariants now apply to direct domain calls and both external adapters.
- Initial readiness, operation-specific status eligibility and review/correction recipients are domain-owned and have pure tests.
- Shared JSON utilities no longer decode season conditions, and planning no longer retains unused work repositories.
- Season-owned lifecycle commands now own activation, completion, archive and emergency persistence/audit. Foreign callers pass immutable evidence and retain the outer transaction.
- Monitoring executes response effects through the public response command. Response owns incident/correction persistence, notifications and emergency coordination.
- Identity owns lifecycle authorization through a public scalar-fact contract. Business modules no longer import workflow authorization.
- Immutable owner query contracts replace foreign repository reads. Public command facades map frozen drafts/results without exposing mutable owner aggregates or re-reading command results after commit.
- Season owns resource-evidence and minimal readiness ports. Workflow implementations gather public facts. The ritual domain implements its public readiness evaluator, removing source cycles without moving decisions into workflow code.
- Java syntax-based dependency checks include API and FQN references; foreign implementation access and capability cycles are rejected. Explicit immutable values declared by public contracts remain supported values, not mutable owner data.
- Runtime database credentials are separate from Liquibase/schema ownership. Migration 015 grants bounded runtime permissions. Actual PostgreSQL tests cover audit mutation/DDL/escalation denials and the supplied login bootstrap. Existing volumes require the documented DBA rollout.
- Existing REST/JSON contracts are preserved. Java package compatibility for serialized sessions is not preserved; an operator-controlled sign-in rollout is required.

## Still required

The [eighth review](../reviews/architecture-review-pass-8.md) closes AR-01 for the inspected owner-query/public-facade/source-cycle boundary. Global source, dependency, recursive contract and alias tests protect these seams. Overall acceptance remains BLOCK because AR-03 and AR-04 remain HIGH.

Do not weaken ARCH.md or use empty packages/forwarding wrappers to claim completion. The facades perform actual isolation/mapping; owner domain policies retain business decisions. Public immutable value types may be declared by contracts, but repositories, policies and mutable aggregates remain owner-internal.

Resource revision binding and durable external operations are also pending. The deployed database still requires verification of its restricted login and credential rollout. A local transaction cannot undo a successful external call. Periodic backups and triggers alone cannot establish absolute administrator immutability or zero data loss.

## Verification

Use red–green–refactor for new architecture constraints and defects. Preserve behavior/API/RBAC tests while updating source-layout assertions to the new design. Run the complete backend suite under Java 17 with PostgreSQL tests, plus frontend tests/lint/build. A compiler release flag on Java 24 is not Java 17 runtime verification.

Review acceptance remains: no HIGH or CRITICAL findings. The initial six passes ended BLOCKED. The user then authorized six further passes (7–12). If pass 12 remains blocked, stop implementation and report the unresolved findings for user decisions. Do not treat intermediate bounded fixes as full AR-01 acceptance.
