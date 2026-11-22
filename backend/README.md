# SeasonPlan backend

Java 17, Spring Boot 3.2, Maven and PostgreSQL 14. The backend remains one application and one Maven artifact. The target design is [ARCH.md](../ARCH.md). This refactor does not introduce microservices.

## Capability packages

Production sources are under `src/main/java/com/seasonplan`:

| Package | Responsibility |
|---|---|
| `season` | Plans, lifecycle and plan validation policies |
| `pollen` | Reservation values, resource rules and the external Pollen Tree boundary |
| `work` | Tasks, assignments, talent compatibility and scheduling policies |
| `ritual` | Sessions, launch/completion decisions and readiness policies |
| `monitoring` | Metric values, replay checks, pinned-rule selection and reaction specifications |
| `response` | Incidents, corrective actions, resolution and escalation rules |
| `reporting` | Immutable report versions, KPI, source snapshots and approval/archive rules |
| `reference` | Execution-rule snapshots, metric catalog and seasonal requirement catalog |
| `identity` | Account domain, public actor contracts and authentication adapters |
| `audit` | Public audit contract, replay identity and append-only persistence |
| `notification` | Outbox/inbox contracts and JDBC/mail delivery adapters |
| `workflows` | Cross-capability coordination and existing lifecycle REST endpoints |
| `bootstrap` | Spring composition and security configuration |
| `shared` | Common time/validation values and technical support |

The old production `lifecycle` and `security` packages are removed. Some test packages keep their old names during migration; these are not production forwarding wrappers.

A capability uses these layers where needed:

```text
<capability>/
  api/                       Public Java contracts
  domain/                    Entities, value objects and pure decision policies
  application/               Command/query orchestration
    port/                    Required persistence and integration contracts
  adapter/
    in/web/                  REST boundary
    out/persistence/         JDBC implementation
    out/http/                External HTTP implementation
    out/fake/                Explicit educational adapters
```

Do not create empty packages for unimplemented features. Not every capability currently has a complete public API. **Capability isolation passed review 8 for the inspected boundary:** owner query/command contracts now hide foreign repositories and mutable aggregates. Public facades copy immutable input/output facts. Workflow implementations collect resource/readiness evidence through public ports; season does not import pollen or ritual. Package separation alone is not release acceptance. See the [refactor decision](../docs/decisions/0006-capability-domains-and-use-case-orchestration.md).

## Domain decisions and application orchestration

Business decisions now have explicit domain owners:

- `SeasonPlanPolicy`: required data, calendar findings, seasonal-window conflicts, catalog membership, resource-review decisions and readiness errors.
- `TaskPreparationPolicy`: task validity and immutability, categories, participation/talent evidence, confirmed participants and schedule conflicts.
- `RitualReadinessPolicy`: required categories and roles, completed work, confirmed participants, resources, checklist conditions and critical blockers.
- `RitualPolicy`: attempt selection, readiness/event evidence, completion outcomes and emergency diagnostics/failure rules.
- `ClimateMetric` and `MetricRulePolicy`: deviations, metric units, observation replay identity and pinned target/rule selection.
- `MetricReactionPolicy`: incident/correction specifications, response deadlines and alert recipients.
- `IncidentResponsePolicy`: incident scope, resolution guards, escalation level and recipient-role selection.
- `ReportPolicy` and `SeasonReport`: canonical snapshots, source changes, version selection, completion blockers and KPI.
- `AccountValidation` and `AccountAdministrationPolicy`: account fields, administrator authority and credential evidence.
- `PollenReservation`, `PollenCapacityPolicy` and `PollenConsumptionPolicy`: transition/replay invariants, positive quantities, capacity checks and consumption warnings.
- `AuditReplayIdentity`: exact replay versus conflicting audit identity.

Policies take supplied facts. They do not query repositories, call HTTP, access Spring or write records. Application methods obtain the actor, start the transaction, load current facts, call the domain, persist the result and request side effects. Transport validation, SQL mapping, cryptographic hashing and delivery retry mechanics stay outside the business domain.

The seasonal requirement table was moved unchanged from the old configuration adapter to the explicitly labelled educational `SeasonalRequirementsCatalog`. It is not a newly approved business catalog. Plan creation still requires complete configured rule versions, tolerances, owners, response times, event requirements and incident rules. Each plan stores its selected execution snapshot. Missing legacy metadata fails closed; it is not backfilled with guessed values.

## Public interfaces and compatibility

- Controllers use `SeasonPlanning`, `PollenOperations`, `WorkPreparation` and public identity contracts.
- Audit consumers use `audit.api.AuditTrail`, not an audit repository or concrete implementation.
- Existing REST paths, JSON shapes and frontend API calls remain compatible. The `/api/v1` paths in ARCH.md remain a proposed API outline, not a breaking route migration in this change.
- The Pollen Tree is the resource authority. The application does not write a local stock balance. The educational fake simulates provider state behind the same external port.
- Reservation request/confirmation/rejection save immutable pollen intent before remote mutation and a receipt before local audit/application. Provider correlation is generated independently of the caller's requestId. Missing requestId uses a stable application key scoped to actor, plan material revision and amount; send an explicit new UUID for a deliberate new identical request.
- An uncertain reservation operation returns HTTP 503 with `code=POLLEN_OPERATION_PENDING` and `operationId`. A `POLLEN_GUARDIAN` can call `POST /api/pollen-operations/{operationId}/reconcile` with the normal session and CSRF token after restart or client-key loss. This command only performs safe provider reads and local completion; it never repeats a provider POST or changes the original actor/payload. Exact terminal decision GETs can reconcile decisions. Request lookup is optional: the educational fake supports it, but the unverified REST provider contract does not, so such unknown requests remain blocked. No manual success override is provided.
- Consumption also uses a durable intent and independently committed receipt (migration 018). It resolves the notification outbox before provider access, rejects ambient transactions, and performs spending without a season lock or database transaction. Local audit, warnings and completion commit together. Known receipts survive local failure; retry returns the original event snapshot without another provider mutation. New spending still requires ACTIVE status and a confirmed agreement. Consumption evidence never creates a reservation funding binding.
- Consumption preserves the provider's existing `(planId, clientEventId)` identity. Exact historical event lookup can prevent a second spend under a pre-journal key; a changed historical amount is rejected. Existing audit identity and provenance are not rewritten. Conflicting historical actor/audit evidence fails closed. The educational fake supports exact event lookup and authoritative pre-dispatch absence; the proposed REST adapter does not. Therefore the owner does not POST consumption through that unverified adapter. No provider lookup route or exactly-once guarantee is assumed. Absence after a dispatch claim never permits redispatch.
- `PollenOperations.reconcileConsumption(operationId, role, actorId)` recovers using exact event reads or local completion and preserves the original intent actor. It uses the existing consumption permission. Consumption remains a core-only Java capability; no execution HTTP route or browser recovery screen is claimed. Unknown or quarantined consumption blocks new resource mutations, settled report usage and funding evidence. Accepted mismatched/ineligible receipts remain durable and quarantined; no manual success override or unspecified season recovery is provided.
- [Independent review 9](../docs/reviews/architecture-review-pass-9.md) closes AR-03/AR-04 for the integrated implementation: zero HIGH and zero CRITICAL findings in the reviewed safety scope. This is not full feature completion or production-readiness approval.
- Default/production calendar validation fails closed. The permissive calendar fake is allowed only under `dev`/`test`, with `prod` absent. No real calendar adapter is claimed.
- A critical incident enters `EMERGENCY` in the incident/metric transaction, with its audit record. Diagnostics and mitigation remain available. An existing running ritual can record diagnostic events and explicit failure, but cannot activate a season in emergency. Recovery is not inferred from severity reduction or incident closure.

## Transactions and persistence

`CommandTransaction` serializes commands using the season row lock. Window-conflict decisions also use a shared transaction-scoped PostgreSQL advisory lock. Confirmed participant assignment and task moves acquire participant-scope locks in sorted order, then check all relevant seasons. Domain policies evaluate the facts after those locks are held.

State, audit and outbox changes use the same local command transaction. Migration `014-emergency-and-audit-truncate.sql` adds the fixed SRS status set, emergency audit action and database TRUNCATE rejection for audit data. Existing migrations were not rewritten.

Migration `015-runtime-database-privileges.sql` separates runtime permissions from migration ownership. Compose provisions a restricted login through a NOLOGIN grant role. Runtime can SELECT/INSERT audit records, but cannot change them, alter guards, assume owner roles or create shadow tables. Spring Liquibase uses separate credentials. See the [database identity rollout](../docs/operations/database-runtime-identity.md), especially the existing-volume steps. Do not delete a volume to apply this change.

This does **not** establish privileged administrator immutability or durable recovery of remote mutations. Protect migration credentials separately; a privileged DBA can still change schema and data.

## Session rollout after the package move

The serialized Spring principal class moved from `com.seasonplan.security.infrastructure.spring.LocalUserDetails` to `com.seasonplan.identity.adapter.out.spring.LocalUserDetails`. Serialized role class names also changed.

Before deployment:

1. Stop old application instances. Do not mix old and new principal classes against the same session store.
2. Use the operator-approved session invalidation/expiry procedure and require a new sign-in. The application does not automatically delete session or account data.
3. Start the new application and verify login, logout, CSRF and role authorization.

Do not delete users, credentials, business records or audit data as a session migration step. If uninterrupted old-session compatibility is required, design and test an explicit serialization migration before deployment.

## Verification on Windows

Select an actual JDK 17. Do not use the host default Java 24 as evidence of Java 17 runtime testing.

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
mvn.cmd -version
mvn.cmd -f .\backend\pom.xml clean verify
npm.cmd --prefix .\frontend test -- --run
npm.cmd --prefix .\frontend run lint
npm.cmd --prefix .\frontend run build
```

The PostgreSQL tests require Docker. For this host's Docker API compatibility with Testcontainers 1.20.4:

```powershell
$env:DOCKER_API_VERSION = '1.44'
mvn.cmd -f .\backend\pom.xml '-Dapi.version=1.44' clean verify
```

The agent also verified the backend using the cached `seasonplan-backend:latest` development image with Temurin 17.0.17, Maven, the Docker socket and PostgreSQL 14 test containers. Native FreeBSD deployment and live external APIs were not verified.

Architecture tests check capability locations, framework-free cores, public controller contracts, audit isolation, pollen integration access and selected domain-decision placement. Global source guards reject foreign repository access. `CapabilityDependencyTest` parses Java syntax to check the complete capability graph, including API and fully qualified references, and rejects cycles and foreign implementation/policy access. Root application wiring belongs to bootstrap. Explicit immutable values declared by public contracts are allowed; mutable owner aggregates are not. Pure `DomainPoliciesTest` and `PollenReservationInvariantTest` exercise business rules without repositories or Spring. `RuntimeDatabasePrivilegesTest` checks actual PostgreSQL privilege denials, including temporary-table shadowing and owner-role escalation; account persistence also runs through the real Spring runtime datasource with separate Liquibase credentials. Existing API, RBAC, transaction and PostgreSQL tests remain in place.

## Remaining scope and risks

The [sixth review](../docs/reviews/architecture-review-pass-6.md) ended the initial phase with **BLOCK**. Three HIGH findings remained: AR-01 (capability ownership), AR-03 (revision-bound funding) and AR-04 (durable external operations). AR-08 and the reviewed AR-17 gaps were corrected. The user initially authorized six further review/fix passes (7–12), then limited the latest continuation to **one iteration maximum**. This continuation stopped after its single verification/review checkpoint. Review 9 met the zero-HIGH/CRITICAL gate for the reviewed integrated safety repairs.

The [eighth review](../docs/reviews/architecture-review-pass-8.md) closed AR-01 for the reviewed ownership/API/dependency boundary. At that checkpoint, AR-03 and AR-04 remained HIGH and the verdict was BLOCK.

The [ninth review](../docs/reviews/architecture-review-pass-9.md) independently closes AR-03 and AR-04 and retains AR-01/08/17 as closed. Its verdict is **ACCEPT for the reviewed safety scope**, with zero HIGH/CRITICAL findings. Final verification: **562 Java 17 backend tests**, **54 frontend tests**, both builds passed; lint has zero errors and one existing warning. Retained MEDIUM/LOW findings and integration/deployment limits remain; no further iteration was started.

Known remaining work includes:

- Preserve the independently reviewed owner-query/facade/cycle boundary and resource-safety guarantees during later authorized work.
- Address retained MEDIUM/LOW findings separately; this review does not close their whole inventory.
- Verify real-provider capabilities before replacing unsupported reconciliation; keep uncertain outcomes blocked without unsafe replay.
- Verify the restricted runtime identity and controlled credential rollout on the deployed database; existing volumes are not automatically reprovisioned.
- Preserve immutable parameter history and reject stale browser edits.
- Complete authoritative reservation reload, task category/completion UI and role-to-user alert delivery.

Reporting persistence/export composition, execution REST/UI routes, live sensors and the escalation scheduler remain incomplete vertical slices. Consumption remains core-only and fails before spending when its separate outbox is unconfigured. Do not claim these features are production-ready merely because their domain/application tests exist.
