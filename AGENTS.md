# Purpose

This document defines mandatory implementation rules for coding agents working on this project. Unless explicitly instructed otherwise, these requirements take precedence over agent preferences and defaults.



# Priority Order
When making implementation decisions, follow this order:

1. Explicit user instructions.
2. Business rules defined in project documentation.
3. This AGENTS.md file.
4. Existing architecture and established patterns.
5. Agent discretion.



# Development Principles

## Mandatory: Red-Green-Refactor TDD

All production code must be developed using Test-Driven Development.

Follow this cycle:

### Red

* Write an automated test describing the required behavior.
* Verify the test fails for the expected reason.

### Green

* Implement the smallest possible change that makes the test pass.
* Do not implement speculative functionality.

### Refactor

* Improve the design without changing behavior.
* Remove duplication.
* Improve readability and naming.
* Keep all tests passing.

### TDD Rules

Agents MUST:

* Never write production code without a failing test first.
* Add regression tests before fixing defects.
* Prefer small development cycles.
* Protect business rules with automated tests.
* Use:

  * Unit tests for domain logic.
  * Integration tests for persistence, APIs, and framework boundaries.
* Run relevant tests before considering work complete.



# Incremental Development

The system is developed one use case at a time.

Agents MUST implement complete vertical slices instead of partially implementing future functionality.

For each use case:

* Implement only what is currently required.
* Fully test the implemented slice.
* Preserve compatibility with existing functionality.
* Avoid speculative implementations.
* Leave the system in a stable state before moving on.



# Mocks and Temporary Implementations

Future components and integrations will often be unavailable.

Agents MUST unblock development by introducing temporary implementations.

Use:

* Interfaces
* Mock implementations
* Stub services
* Fake repositories
* Placeholder adapters
* In-memory implementations

Temporary implementations MUST:

* Match production interfaces.
* Be deterministic.
* Support automated testing.
* Be easy to replace later.
* Avoid unnecessary behavior.

When real implementations become available:

* Preserve existing contracts.
* Reuse existing tests.
* Verify behavioral compatibility.
* Remove obsolete placeholders.



# Technology Stack

## Backend

* Java 17 (OpenJDK 17+)
* Spring Boot 3.2 / Spring Framework 6
* Maven
* REST APIs
* OpenAPI 3.0

## Frontend

* React 18
* JavaScript (ES6+)
* HTML5
* CSS3

Supported browsers:

* Chrome
* Firefox
* Edge

## Database

* PostgreSQL 14

## Infrastructure

* Docker
* GitHub Actions

## Target Runtime

* FreeBSD 14.4-STABLE
* amd64
* OpenJDK 17.0.12

## Development Host Environment

The current development host is Windows 10 Pro.

Agents working in this repository MUST account for the following local environment:

* Workspace path: `X:\projects\seasonPlan`.
* Primary shell: Windows PowerShell 5.1 Desktop.
* Primary scripting language for local automation: PowerShell.
* Prefer PowerShell cmdlets and Windows-compatible commands in examples, scripts, and troubleshooting steps.
* Use Windows path syntax in local instructions unless documenting target-runtime behavior.
* Avoid assuming Bash, GNU coreutils, or Unix shell semantics are available on the development host.
* PowerShell script execution is restricted by policy on this host; direct `npm` invocation may fail because `npm.ps1` cannot be loaded.
* Use `npm.cmd` from PowerShell when invoking npm commands unless the execution policy is intentionally changed.
* Active Java on PATH at the time this file was updated: Oracle JDK 24.0.2.
* `JAVA_HOME` is not set at the time this file was updated.
* Maven is available as Apache Maven 3.9.11 and currently runs with Java 24.0.2.
* The project target remains Java 17; configure builds, toolchains, CI, and documentation so production code is compiled and tested against Java 17 despite the local PATH Java version.
* Node.js is available as v24.12.0.
* npm is available as 11.6.2 through `npm.cmd`.
* Git is available as 2.51.0.windows.1.
* Docker CLI is available as 28.4.0, but Docker configuration access may be restricted for `C:\Users\pochk\.docker\config.json`.
* ripgrep is available as `rg` 15.1.0 and should be preferred for text/file searches.
* Some Windows system inventory commands may be denied by local permissions; do not require WMI/CIM access for normal project workflows.

Agents MUST NOT introduce technologies outside this stack without explicit justification.


# Architecture Rules

## Client–Server Separation

* Frontend communicates only through backend APIs.
* Backend owns business logic.
* PostgreSQL stores persistent data.

## Communication

Use:

* HTTPS (TLS 1.2+)
* REST
* JSON (UTF-8)

WebSocket may be used where realtime communication is required.

## Dependency Direction

Domain logic MUST remain independent of:

* External systems,
* Framework-specific implementations,
* Infrastructure concerns,
* Mock implementations.

Prefer dependency inversion and interface-driven design.



# Domain Modules

The system consists of:

* SeasonPlan
* PollenReservation
* MagicTask
* FairyAssignment
* RitualSession
* ClimateMetric
* MagicIncident
* CorrectionAction
* SeasonReport
* AuditLog

Model these as separate domain concepts with clear responsibilities.



# Mandatory Business Rules

## SeasonPlan Lifecycle

Only these statuses are allowed:

DRAFT → SUBMITTED → VERIFIED → APPROVED → RITUAL_READY → ACTIVE → COMPLETED → ARCHIVED

Do not introduce additional statuses.

## Status Preconditions

APPROVED:

* Requires approved PollenReservation.

RITUAL_READY:

* Requires completion of mandatory tasks and checklists.

ACTIVE:

* Requires successful RitualSession validation.

COMPLETED:

* Forbidden while CRITICAL MagicIncidents remain open.

## AuditLog

Audit records are immutable.

Forbidden:

* Updating audit entries.
* Deleting audit entries.

Audit history must be preserved completely.

## Pollen Operations

All pollen-related functionality MUST use Pollen Tree integrations.

Forbidden:

* Direct database updates.
* Bypassing integration services.



# External Integrations

Integrations MUST be isolated behind interfaces.

## Pollen Tree

* REST
* HTTPS

## Natural Calendar

* REST
* HTTPS

## Metric Sensors

* REST and/or WebSocket
* HTTPS

All integrations MUST be mockable.

Business logic MUST NOT depend directly on concrete integration clients.



# Security Requirements

Implement:

* Authentication
* RBAC authorization
* TLS 1.2+
* API Keys and/or mutual TLS for external integrations

Enforce security at API boundaries.



# Performance Targets

* ≥ 50 concurrent active users.
* Main screens load within 3 seconds.
* Standard operations complete within 5 seconds.
* Save/create/update operations complete within 5 seconds.
* Process at least 100 events per minute.
* ClimateMetric refresh interval: 1–5 minutes.
* Minimum availability: 80%.

## Backup Requirements

* Perform backups at least every 4 hours.
* AuditLog data loss is unacceptable.



# User Interface Requirements

Required screens:

* Dashboard
* SeasonPlan details
* Task list
* Incident journal
* ClimateMetric monitoring
* AuditLog viewer
* SeasonReport viewer/export

Support:

* Validation messages
* Status-based field locking
* Filtering and searching
* Graphical visualizations
* PDF export
* HTML export



# Definition of Done

Work is complete only if:

* All relevant tests pass.
* New behavior is covered by automated tests.
* Business rules are enforced.
* No speculative functionality was added.
* Temporary mocks are documented when introduced.
* Existing functionality remains compatible.
* The implementation conforms to this AGENTS.md.
