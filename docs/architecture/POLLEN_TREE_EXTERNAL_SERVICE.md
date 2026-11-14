# Pollen integration: one application service, one external client

## Purpose and requirements

The [Vision](../../arch_docs/VISION.md) §4.1 identifies SeasonPlan as a coordination platform integrated with the **external Pollen Tree reservation API**. Its §5.2 requires reservation confirmation, consumption monitoring and shortage warnings; §6 explicitly requires all pollen operations to use that API and prohibits direct writes. Stock/tree characteristics are not internal entities.

The [SRS](../../arch_docs/SYSTEM_REQUIREMENTS_SPECIFICATION.md) adds:

| References | Integration responsibility |
| --- | --- |
| §2.2–2.4; FR-14–19 | Guardian-controlled requests/decisions, capacity checks and launch blocking; external resource authority |
| FR-9; FR-19; FR-36–40 | Authoritative confirmed reservation before approval/readiness |
| FR-20–23; FR-74 | External consumption, comparisons, warnings and report inputs |
| FR-78–81; RR-1/2 | Durable decisions/audit and preservation of resource information; not a requirement to duplicate the external database locally |
| FR-87 | Shortage warnings |
| DC-7/8/12/13; §3.6.2–3.6.4 | REST/HTTPS, JSON UTF-8, TLS 1.2+, API-key or mTLS authentication, mockable educational integration |

The user's explicit ownership decision is that **reservation persistence belongs to Pollen Tree**, not SeasonPlan. Existing [ST-00 business decisions](ST-00-specification-resolution.md) still define confirmation, grams, amendments and thresholds; their incomplete implementation is not expanded by this simplification.

## Current design

```text
REST routes / approval / readiness / completion / compatibility facade
                                |
                          PollenService
                                |
                    PollenTreeClient (interface)
                                |
                RestPollenTreeClient -> external Pollen Tree
                FakePollenTreeClient -> dev/test simulation

External results via PollenService -> response / lifecycle checks / report inputs
Our decisions    -> immutable AuditLog
Consumption risk -> notification outbox
```

There is **one application entry point for every pollen operation**: `application/pollen/PollenService.java`.

- Requests, confirms and rejects reservations.
- Reads external capacity, the latest reservation, the authoritative confirmed reservation, reservation details and usage.
- Records consumption for ACTIVE seasons.
- Enforces roles/input validation, audits decisions and consumption, and emits threshold notifications.
- Does not own stock, allocate pollen itself, or persist reservations.

There is **one external API contract**: `application/port/PollenTreeClient.java`, used only behind `PollenService`. Approval, preparation/readiness, completion/report and compatibility services depend on `PollenService`, never on the client. Configuration chooses the adapter; it does not offer an alternative access path to application code.

`PollenService` is the application facade: it owns our validation, authorization, audit and warning orchestration. `RestPollenTreeClient` is not another business service: it only implements the external HTTPS/JSON contract, response validation and transport-error translation. Keeping these two responsibilities separate makes the external integration replaceable without changing its consumers.

The facade returns client-independent `PollenReservation`, `PollenCapacity` and `PollenUsage` values, including the provider source version for report freshness checks. Latest-reservation/usage absence stays `Optional.empty()`; provider failures propagate instead of becoming missing data. Reads do not cache, audit, resolve the consumption outbox or access local persistence.

`FakePollenTreeClient` simulates the same provider contract with deterministic IDs, detached results, shared cross-plan capacity and replay-safe operations. It is ephemeral and allowed only in explicitly configured dev/test runtimes.

## Removed complexity

Removed:

- `PollenReservationRepository`, `JdbcPollenReservationRepository`, `InMemoryPollenReservationRepository`;
- separate `PollenReservationService` and `PollenConsumptionService`;
- ambiguous/split `PollenTree` and `PollenTreeReservations` interfaces;
- reservation projection writes and the transaction previously required to coordinate projection plus audit;
- direct external-client dependencies from approval, preparation, completion and the compatibility facade.

`PollenReservation` remains a returned external reservation representation used by the API, **not a locally persisted entity**. `PollenUsage` is an immutable usage value used in reports, not an inventory ledger.

No active application code reads or writes `pollen_reservations`. The table in the already-existing Liquibase migration is left untouched solely to avoid rewriting applied migration checksums or silently deleting historical data. It has no repository, application ownership or runtime role. PostgreSQL tests assert that new pollen commands leave it empty. Any later legacy-data cleanup requires a separate migration/retention decision.

Internal AuditLog and approved report snapshots remain legitimate SeasonPlan persistence. They do not act as a reservation/stock authority.

## Audit, retries and consumption warnings

Reservation decisions execute through the client, then append our audit record. No local reservation completion transaction is needed: only one immutable audit append remains. The audit comment includes the external reservation UUID and decision reason. Stable event IDs deduplicate identical decision retries; timestamps/history are never updated or deleted.

If the provider succeeds but our audit write fails, retrying the identical decision obtains the external result without reallocating pollen and repairs the missing audit. Changed terminal decisions are rejected. External mutations cannot be undone by our database rollback; no automatic reconciliation worker or distributed transaction is claimed.

Consumption uses the same `PollenService` and client. Amounts normalize to scale 3 with HALF_UP. Only ACTIVE seasons may consume. At 80% and 100%, warnings target the Pollen Guardian and Talent Coordinator; over 100% also includes the Chief Guardian. Outbox keys and audit event IDs prevent duplicate local effects for the same plan/event retry. The provider must return the original event's post-consumption result, not a later aggregate.

Consumption still needs the existing UC-4 transaction/outbox and persistence integration. The outbox is resolved only when consumption is invoked, **before any external spending**; a missing adapter fails explicitly. Reservation routes do not need an outbox. `JdbcCommandTransaction` remains the local transaction adapter for consumption, not a reservation repository/projection mechanism. This refactor does not add consumption REST routes or claim to complete the UC-4 audit/outbox schema and delivery slice.

## Proposed external REST contract

No provider OpenAPI, URL or credentials were supplied. These are **proposed, contract-tested routes**, not verified compatibility with a deployed provider. Agree them with the provider or adapt only `adapter/pollentree/RestPollenTreeClient.java` and its tests.

Paths are relative to `POLLEN_TREE_BASE_URL`; requests send `X-API-Key` and accept JSON. POST bodies use JSON UTF-8.

| Method/path | Request / result |
| --- | --- |
| GET `/capacity` | `{availableGrams, version}` |
| POST `/reservations` | `{seasonPlanId, requestId, requestedAmount, actorId}` -> reservation |
| GET `/reservations/{id}` | Reservation or 404 |
| GET `/season-plans/{id}/reservations/latest` | Latest reservation or 404 |
| GET `/season-plans/{id}/reservations/authoritative` | Proposed `{authoritativeKnown: true, reservation: <confirmed reservation or null>}`; 404 or missing marker means unavailable, not no authority |
| POST `/reservations/{id}/confirm` | `{confirmedAmount, comment, actorId}` -> confirmed reservation |
| POST `/reservations/{id}/reject` | `{comment, actorId}` -> rejected reservation |
| GET `/season-plans/{id}/usage` | Usage or 404 |
| POST `/season-plans/{id}/consumptions` | `{clientEventId, grams}` -> original event's usage result |

Reservation: `{id, seasonPlanId, requestId, requestedAmount, confirmedAmount, status, comment, confirmedBy}`. Current reservation amounts retain the legacy positive integer-grams API; fractional/overflow responses are rejected, not truncated. Incoming `DRAFT`/`PENDING` maps to local response `PENDING`. Pending results cannot contain decision metadata. Confirmed/rejected results require valid decision values/actor; rejection requires a reason.

Usage: `{seasonPlanId, reservationId, confirmedGrams, actualGrams, version, clientEventId}`. Totals and correlation fields are validated. The original retry snapshot may be older than the current summary.

Provider requirements:

- Request IDs deduplicate the same plan/amount/actor; changed payloads are rejected. Existing browser request routes accept optional `requestId`; omitted IDs are freshly generated, so separate calls are not deduplicated automatically.
- Confirm/reject replay is idempotent by reservation UUID plus exact amount/comment/actor. The adapter first reads the reservation and permits pending state or an exactly matching terminal replay. That preflight is not a lock; the provider must enforce state/capacity atomically at POST time.
- Consumption deduplicates `(seasonPlanId, clientEventId)`, rejects changed normalized amounts and returns the original event result even after later events.
- Only real lookup/summary 404 means absence; the proposed authoritative endpoint must return a 200 envelope even when no confirmed reservation exists. Missing mutation targets become 404. Mutation 400/409/422 becomes sanitized field validation. Authentication errors, 429, 5xx, redirects, timeout/I/O and malformed/inconsistent results become generic SeasonPlan API 503: `Pollen Tree is unavailable`.
- There is no silent local/fake fallback and no automatic command retry. UC-2 checks both the latest record and this authoritative response; a DRAFT or REJECTED replacement does not supersede an earlier CONFIRMED record. A rejected reservation without authority returns the plan for revision with its reason. The authority endpoint is unverified and requires provider agreement.

Browser reservation routes and response fields remain compatible. The browser never receives the external API key or calls the provider directly.

## Configuration

Property prefix: `integrations.pollen-tree`.

| Environment variable | Property | Default/rule |
| --- | --- | --- |
| `POLLEN_TREE_MODE` | `mode` | `rest`; dev/test resources explicitly select `fake` |
| `POLLEN_TREE_BASE_URL` | `base-url` | Required absolute HTTPS URL in REST mode |
| `POLLEN_TREE_API_KEY` | `api-key` | Required nonblank key in REST mode |
| `POLLEN_TREE_CONNECT_TIMEOUT` | `connect-timeout` | `2s`, allowed 1ms–30s |
| `POLLEN_TREE_READ_TIMEOUT` | `read-timeout` | `3s`, allowed 1ms–30s |
| `POLLEN_TREE_FAKE_CAPACITY_GRAMS` | `fake-capacity-grams` | `1000`, development simulation only |

URLs cannot contain user information, query or fragment. TLS is restricted to 1.2/1.3, normal certificate/hostname validation is retained, and redirects are disabled. Missing/invalid REST settings fail startup. Fake mode requires dev/test and is always forbidden with any active `prod` profile. Existing Docker development uses dev/fake explicitly.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:POLLEN_TREE_MODE = 'rest'
$env:POLLEN_TREE_BASE_URL = 'https://pollen-tree.example/api'
$env:POLLEN_TREE_API_KEY = '<provided securely; never commit a real key>'
```

## Verification and remaining work

Red-first tests cover the consolidated service/removed abstractions, audit-only recovery, readable external audit references and missing-outbox failure before spending. New architecture tests enforce the exclusive facade boundary and client-independent public return types. Read tests preserve absence, source versions, propagated outages and freedom from local side effects. Existing secure transport, capacity, role, approval/readiness, report and retry tests remain covered. An independent read-only reviewer found no issues in the facade-boundary change.

On checksum-verified Temurin JDK 17.0.21:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
# This host's Docker 29 requires an API override for Testcontainers 1.20.4:
$env:DOCKER_API_VERSION = '1.44'
mvn.cmd -f .\backend\pom.xml '-Dapi.version=1.44' clean verify
npm.cmd --prefix .\frontend test -- --run
npm.cmd --prefix .\frontend run build
```

Backend verification: **224 tests pass, including all 6 PostgreSQL integration tests** (zero failures/errors/skips). Audit-failure retries and absence-of-local-reservation-write assertions now execute against PostgreSQL 14. The initial full run exposed Docker's rejection of the old client's API 1.32; the test-process-only override above enabled execution, without changing dependencies or production configuration. A previously unexecuted recovery test's injected fault now uses a persistence exception, avoiding Spring's translation of a generic IllegalStateException. The separate non-Docker run also passed all 218 tests.

Frontend: **35 tests pass** and the production build succeeds; frontend source and HTTP contract remain unchanged.

Remaining product work is explicit: ST-00's calculated need, canonical decimal/DRAFT reservation API and linked amendments, shortage-delivery integration, and the rest of the UC-4/UC-5 runtime/API/persistence slices. No new SeasonPlan statuses are introduced. Live provider compatibility, TLS handshakes and provider atomicity/idempotency require a real provider environment. Old local rows are not automatically imported into that provider.
