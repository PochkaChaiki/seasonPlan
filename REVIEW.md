# Project Review

Review date: 2026-06-07

Scope reviewed:

* Backend Spring Boot lifecycle, persistence, migration, security, and tests.
* Frontend React shell, build setup, and tests.
* Docker Compose, OpenAPI stub, architecture notes, and project instructions.

## Executive Summary

The project has a coherent first vertical slice for SeasonPlan lifecycle operations, with service-level tests, controller tests, PostgreSQL persistence tests, Flyway migrations, and a minimal React shell for required screens. The current tree verifies successfully:

* `mvn test` in `backend`: passed, 15 tests.
* `npm.cmd test -- --run` in `frontend`: passed, 1 test file.
* `npm.cmd run build` in `frontend`: passed.
* `npm.cmd run lint` in `frontend`: passed.

The largest remaining risks are not syntax-level issues. They are gaps against mandatory project rules: API boundary security is effectively disabled, approval can happen without a confirmed pollen reservation despite AGENTS.md requiring one, Java 17 is not strictly enforced on the local build runtime, and the OpenAPI document is too shallow to serve as an API contract.

## Findings

### High: API Boundary Security Is Disabled

`backend/src/main/java/com/seasonplan/lifecycle/config/SecurityConfiguration.java:13-14` disables CSRF and permits every request:

```java
csrf -> csrf.disable()
authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
```

This conflicts with AGENTS.md security requirements for authentication, RBAC authorization, TLS 1.2+, and API-boundary enforcement. The services do enforce roles through `X-Actor-Role`, but the boundary trusts caller-supplied headers without authentication. Any client can impersonate any role by setting headers.

Recommended next step: add an authenticated request model and enforce roles at the controller/security layer. Keep service-level role checks as defense in depth.

### High: APPROVED Transition Does Not Require Approved/Confirmed Pollen Reservation

AGENTS.md says `APPROVED` requires approved `PollenReservation`. Current implementation in `SeasonPlanService.approve()` only blocks the latest rejected reservation:

`backend/src/main/java/com/seasonplan/lifecycle/service/SeasonPlanService.java:100-116`

That matches `TASK.md` use case 2, but it is weaker than the mandatory business rule in AGENTS.md. A submitted plan with no reservation can be approved.

Recommended next step: clarify the intended rule, then add a failing regression test before changing production code. If AGENTS.md governs, approval should require the latest pollen reservation to be `CONFIRMED` or an equivalent approved state.

### Medium: Java 17 Target Is Compiled, But Java 17 Runtime Is Not Enforced

The POM compiles with release 17, but the Maven enforcer rule allows any Java version from 17 upward:

`backend/pom.xml:122-124`

The verified backend test run used Java 24.0.2. AGENTS.md states the target runtime is OpenJDK 17.0.12. Java 24 can hide runtime compatibility problems, warnings, or dependency behavior differences.

Recommended next step: add Maven toolchains or CI setup that runs tests with JDK 17, and consider tightening local verification documentation so `mvn test` is known to execute under Java 17.

### Medium: OpenAPI Document Is Not a Usable Contract

`docs/api/openapi.yaml` lists paths and summaries only. It has no operation ids, parameters, request bodies, responses, schemas, error models, or security schemes.

This limits client/server contract testing, generated documentation, and API review. It also does not describe the required `X-Actor-Role` and `X-Actor-Id` headers currently used by controllers.

Recommended next step: extend OpenAPI incrementally for the implemented endpoints first, including request/response schemas and structured error responses.

### Medium: Readiness Checklist Persistence Rewrites Completion Metadata

`JdbcSeasonPlanRepository.save()` deletes all readiness checklist rows and reinserts them on every plan save:

`backend/src/main/java/com/seasonplan/lifecycle/persistence/JdbcSeasonPlanRepository.java:86-94`

For completed items, it stores `completed_by` and `completed_at` from the plan's current `updatedBy` and `updatedAt`, not from the actual checklist completion event. Later saves can overwrite historical completion metadata.

Recommended next step: store completion metadata on the domain object or update checklist items independently so completed audit-like facts are preserved.

### Low: Architecture Documentation Is Placeholder-Only

`docs/architecture/README.md` currently contains only a placeholder. The code already has important architectural decisions: domain services depend on repository/integration interfaces, JDBC adapters implement persistence, and Testcontainers verifies PostgreSQL behavior. Those decisions should be captured before the implementation grows.

Recommended next step: document module boundaries, dependency direction, current temporary integrations, and the persistence strategy.

## Positive Observations

* Domain statuses match the mandatory SeasonPlan lifecycle status list.
* Tests cover creation, validation, submission, approval/return, readiness blocking, role checks, controller errors, Flyway schema presence, and PostgreSQL persistence.
* External-style services are isolated behind interfaces such as `NaturalCalendarService`, `NotificationService`, and repository interfaces.
* Audit log persistence includes database triggers preventing update and delete operations.
* Frontend uses required screen placeholders and has a focused rendering test.
* PowerShell/npm environment constraints are followed by using `npm.cmd`.

## Verification Notes

Initial backend test execution failed because Maven dependency download was blocked by sandbox network restrictions. After allowing Maven dependency access, the first backend compile attempt exposed a transient class-name mismatch in `SeasonPlanService.java`. The current filesystem state no longer contains that mismatch, and the final backend verification passed.

Final commands run:

```powershell
cd X:\projects\seasonPlan\backend
mvn test

cd X:\projects\seasonPlan\frontend
npm.cmd test -- --run
npm.cmd run build
npm.cmd run lint
```

## Suggested Priority Order

1. Add authentication/RBAC enforcement at the API boundary.
2. Resolve the APPROVED pollen reservation rule conflict between AGENTS.md and TASK.md, then protect the selected behavior with tests.
3. Ensure backend tests run on Java 17 in CI/local documented workflow.
4. Expand OpenAPI for implemented endpoints.
5. Preserve readiness checklist completion metadata across saves.
6. Fill in architecture documentation from the implemented slice.
