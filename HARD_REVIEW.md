# Hard Review

Review date: 2026-06-07

Tone requested: strict. So here it is: the project passes tests, but the architecture is already drifting into the usual small-project trap where "it works" is confused with "it is designed." The current implementation is a functional prototype with some good intentions, not a clean architecture.

## Verdict

The project has a reasonable first vertical slice, but the boundaries are soft, the domain model is anemic and mutable, HTTP contracts leak straight into domain objects, and application services know too much about each other. The code is not hopeless. It is just at the exact stage where careless continuation will turn it into a service-layer script pile with JDBC at one end and JSON at the other.

## Critical Findings

### 1. Domain Objects Are Mutable DTOs, Not Domain Objects

`SeasonPlan` is the clearest example:

* `backend/src/main/java/com/seasonplan/lifecycle/domain/SeasonPlan.java:12` uses Jackson annotation directly in the domain.
* `backend/src/main/java/com/seasonplan/lifecycle/domain/SeasonPlan.java:32-129` exposes broad setters for identity, status, timestamps, actor fields, and collections.
* `backend/src/main/java/com/seasonplan/lifecycle/domain/SeasonPlan.java:44-53` returns mutable internal lists directly.
* `backend/src/main/java/com/seasonplan/lifecycle/domain/SeasonPlan.java:68-69` returns a mutable internal map directly.

This violates encapsulation. Any caller can mutate lifecycle state without going through business rules. `setStatus(APPROVED)` is public. `setCreatedAt(...)` is public. The object has no meaningful invariant protection.

This is not OOP. This is a bag of fields with methods arranged around it.

Required direction:

* Make state transitions behavior on the aggregate: `submit(...)`, `approve(...)`, `returnForRevision(...)`, `markRitualReady(...)`.
* Remove public status/timestamp/id setters from normal application flow.
* Return immutable views or copies from collection accessors.
* Keep Jackson annotations out of domain entities. Use request/response DTOs or explicit mappers.

### 2. HTTP API Exposes Domain Objects Directly

`LifecycleController` accepts and returns domain objects:

* `backend/src/main/java/com/seasonplan/lifecycle/api/LifecycleController.java:49` accepts `SeasonPlan` as request body and returns `SeasonPlan`.
* `backend/src/main/java/com/seasonplan/lifecycle/api/LifecycleController.java:85` returns `PollenReservation`.
* `backend/src/main/java/com/seasonplan/lifecycle/api/LifecycleController.java:100` accepts `MagicTask` as request body and returns `MagicTask`.

This couples the public REST API to internal domain shape. It also explains why domain classes are polluted with Jackson annotations. The API layer is not an adapter; it is punching a hole straight into the model.

Consequences:

* Clients can send fields that should be server-owned.
* Domain refactoring becomes API breaking change.
* Serialization concerns dictate domain design.
* Validation is scattered because request shape and aggregate shape are treated as the same thing.

Required direction:

* Introduce command/request DTOs for API input.
* Introduce response DTOs for API output.
* Map DTOs to application commands, not directly to mutable domain entities.

### 3. Clean Architecture Boundary Is Mostly Naming, Not Enforcement

The package layout says `api`, `service`, `domain`, `persistence`, `config`. That looks tidy until reading the contents.

Problems:

* Fake infrastructure adapters live in `service`: `InMemorySeasonPlanRepository`, `InMemoryMagicTaskRepository`, `InMemoryPollenReservationRepository`, `InMemoryFairyAssignmentRepository`, `InMemoryAuditLogRepository`.
* Repository interfaces also live in `service`.
* `AllowingNaturalCalendarService` and `NotificationService` are concrete adapter-ish implementations in `service`.
* Domain imports Jackson annotations in multiple entities.

The direction is not catastrophic yet, but it is muddy. A clean architecture depends on enforced dependency rules, not folder names.

Required direction:

* Split application use cases from ports and adapters:
  * `domain`: pure domain model and domain policies.
  * `application`: use cases and command handlers.
  * `application.port`: repository/integration interfaces.
  * `adapter.web`: controllers and DTO mappers.
  * `adapter.persistence.jdbc`: JDBC implementations.
  * `adapter.fake` or test fixtures: in-memory implementations.
* Add an architecture test, for example with ArchUnit, to prevent domain depending on Jackson/Spring/JDBC.

### 4. Services Are Coupled Into a Procedural Call Chain

`MagicTaskService` depends on `SeasonPlanService`:

* `backend/src/main/java/com/seasonplan/lifecycle/service/MagicTaskService.java:15`
* `backend/src/main/java/com/seasonplan/lifecycle/service/MagicTaskService.java:26`

`FairyAssignmentService` depends on `MagicTaskService`:

* `backend/src/main/java/com/seasonplan/lifecycle/service/FairyAssignmentService.java:15`
* `backend/src/main/java/com/seasonplan/lifecycle/service/FairyAssignmentService.java:26`
* `backend/src/main/java/com/seasonplan/lifecycle/service/FairyAssignmentService.java:64`

This creates service-to-service coupling instead of use cases coordinating ports and aggregates. It is already visible in `hasScheduleConflict`, where assignment logic loads all assignments and calls another service repeatedly to fetch tasks. That is a design smell and a performance smell.

Required direction:

* Use repositories/ports for data needed by the use case.
* Pull cross-aggregate checks into explicit domain/application policies such as `FairySchedulePolicy`.
* Avoid service A calling service B as a substitute for modeling the use case.

### 5. ReadinessChecklistService Uses Setter Injection By Hand

`ReadinessChecklistService` has mutable dependencies and a `configure(...)` method:

* `backend/src/main/java/com/seasonplan/lifecycle/service/ReadinessChecklistService.java:17-20`
* `backend/src/main/java/com/seasonplan/lifecycle/service/ReadinessChecklistService.java:22-32`

This is poor object design. The service can exist in an invalid state. The compiler cannot enforce that dependencies are present. Tests or future configuration changes can accidentally create a half-built object.

Required direction:

* Use constructor injection with final fields.
* Delete `configure(...)`.
* Do not let application services have a two-step lifecycle unless there is a very strong reason. There is none here.

### 6. Lifecycle Rules Live Outside The Aggregate

Status transitions are implemented by services mutating fields:

* `SeasonPlanService.submitForApproval()` sets status directly.
* `SeasonPlanService.approve()` sets status directly.
* `SeasonPlanService.returnForRevision()` sets status directly.
* `SeasonPlanService.markRitualReady()` sets status directly.

The aggregate itself has no control over legal transitions. A domain object that cannot protect its own lifecycle is not an aggregate; it is a persistence record.

Required direction:

* Move transition legality into `SeasonPlan`.
* Application services should orchestrate dependencies, authorization, persistence, and audit, but the aggregate should reject invalid state changes.
* Tests should assert aggregate behavior directly, not only service behavior.

### 7. Security Is Theater At The Boundary

`SecurityConfiguration` permits every request:

* `backend/src/main/java/com/seasonplan/lifecycle/config/SecurityConfiguration.java:13-14`

Controllers then trust `X-Actor-Role` and `X-Actor-Id` headers. That is not RBAC. That is letting the caller write their own badge.

Yes, services check roles. No, that does not make this acceptable. A malicious caller can just send `X-Actor-Role: CHIEF_GUARDIAN`.

Required direction:

* Authenticate the caller.
* Derive actor id and roles from authentication, not user-controlled headers.
* Keep explicit service authorization checks as defense in depth, but stop pretending headers are identity.

## Major Design Problems

### 8. Primitive Obsession Is Everywhere

Important concepts are raw strings and numbers:

* `actorId`
* `fairyId`
* `role`
* `talent`
* `seasonType`
* `territory`
* `requestedAmount`
* `confirmedAmount`

This makes invalid states easy and meaning hard to see. `String role` in `FairyAssignment` is especially weak when roles are domain concepts.

Required direction:

* Introduce small value objects where they carry rules or identity: `ActorId`, `FairyId`, `Talent`, `Territory`, `PollenAmount`.
* At minimum, use enums for closed sets.
* Keep free-form strings only where the domain is genuinely free-form.

### 9. Persistence Logic Rebuilds Child Collections Destructively

`JdbcSeasonPlanRepository.save()` deletes and reinserts child rows:

* `backend/src/main/java/com/seasonplan/lifecycle/persistence/JdbcSeasonPlanRepository.java:74-94`

That is a blunt persistence strategy. It loses row identity and rewrites checklist completion metadata. For something like readiness checklist state, this is not harmless bookkeeping; it is historical data.

Required direction:

* Persist child entities with stable identity.
* Update changed rows intentionally.
* Preserve completion actor/time as facts, not as side effects of saving a whole plan.

### 10. In-Memory Repositories Are Not Isolated As Test/Fake Adapters

The in-memory repositories are under `service`, which makes fake infrastructure look like part of the application layer. That weakens package semantics and invites production code to depend on test conveniences.

Required direction:

* Move in-memory implementations to a fake adapter package or test source set unless they are a supported runtime adapter.
* Document temporary implementations clearly if they are production-visible.

### 11. NotificationService Is A Fake, But Named Like A Real Service

`NotificationService` stores strings in memory:

* `backend/src/main/java/com/seasonplan/lifecycle/service/NotificationService.java:6-13`

This is fine as a fake, but the naming and placement hide that fact. A future developer can easily assume this is the real notification boundary.

Required direction:

* Define a `NotificationPort`.
* Rename this implementation to `InMemoryNotificationAdapter` or keep it in tests.
* Do not let a fake masquerade as an application service.

### 12. API Error Model Is Ad Hoc

Errors are maps of strings to lists. That is serviceable for a prototype, but it is not a carefully designed API contract. Since OpenAPI is also skeletal, clients have no stable contract.

Required direction:

* Define a versioned error response schema.
* Document it in OpenAPI.
* Stop returning anonymous maps from exception handlers as the long-term API shape.

## Test Quality Review

The tests are useful, but they are mostly happy-path and service-level orchestration tests. They prove current behavior, not architecture quality.

Missing tests that matter:

* Architecture boundary tests: domain must not import Jackson, Spring, JDBC, or persistence.
* Domain aggregate tests for status transitions without repositories.
* API contract tests for request/response DTOs once DTOs exist.
* Security tests proving unauthenticated requests fail and caller roles cannot be spoofed.
* Regression test for approval requiring confirmed pollen reservation if AGENTS.md is authoritative.
* Persistence tests for preserving readiness checklist completion metadata across unrelated saves.

Passing tests are good. Passing tests around a compromised design do not make the design clean.

## Frontend Review

The frontend is currently a placeholder shell. That is acceptable for a first slice only if everyone admits it is a placeholder.

Issues:

* No feature structure.
* No API client boundary.
* No state/data loading pattern.
* No validation UX beyond future intent.
* Tests only assert that links exist.

Required direction when real UI starts:

* Organize by feature or route, not by dumping everything into `App.jsx`.
* Introduce an API client module and typed-ish DTO boundaries even in JavaScript.
* Keep component tests focused on user-visible behavior.

## What Is Good

There are a few decisions worth keeping:

* Repository interfaces exist, which is a start.
* JDBC adapters are separated from controllers.
* Tests include service, controller, migration, and PostgreSQL persistence coverage.
* Flyway migrations include useful database constraints and audit immutability triggers.
* The project is using vertical slices instead of pretending the whole system exists.

Those are not small things. But they do not cancel out the boundary and modeling problems.

## Required Cleanup Plan

1. Stop exposing domain entities through controllers. Add DTOs and mappers.
2. Remove Jackson annotations from domain classes.
3. Move fake adapters and ports out of the generic `service` package.
4. Convert `SeasonPlan` from mutable record-like object into an aggregate with guarded transitions.
5. Replace setter injection in `ReadinessChecklistService` with constructor injection.
6. Introduce authentication and derive actor context from it.
7. Add architecture tests to enforce dependency direction.
8. Expand OpenAPI for implemented endpoints.
9. Preserve child entity identity and completion metadata in persistence.
10. Revisit primitive obsession where domain concepts have rules.

## Final Judgment

The project is not a disaster. It is worse in a more common way: it looks clean enough to let people stop caring. Do not. The current structure will not survive many more use cases unless the boundaries are tightened now.

The immediate danger is not that the code fails. It passes. The danger is that future features will be added by copying the current service pattern, and then the domain will become a collection of mutable JSON-shaped objects pushed around by procedural services. That is exactly how clean architecture dies while every package still has a nice name.
