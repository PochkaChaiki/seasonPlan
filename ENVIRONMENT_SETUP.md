# Environment Setup

This document describes the development host requirements, project dependencies, and a possible directory structure for the SeasonPlan system.

The target runtime is FreeBSD 14.4-STABLE on amd64 with OpenJDK 17.0.12. The current development host is Windows 10 Pro, with Windows PowerShell 5.1 as the primary shell.

## Development Host Tools

Install and configure these tools on the Windows development host.

### Required

* OpenJDK 17
  * Required for compiling and testing backend production code.
  * Set `JAVA_HOME` to the JDK 17 installation directory.
  * Put `%JAVA_HOME%\bin` before other Java installations on `PATH`.
  * The local host may have newer Java versions installed, but project builds must target Java 17.

* Apache Maven 3.9.x
  * Required for backend build, dependency management, and tests.
  * Use Maven Wrapper (`mvnw.cmd`) if the project adds it later; otherwise use `mvn`.

* Git for Windows
  * Required for source control and GitHub Actions compatibility checks.

* Docker Desktop or compatible Docker Engine
  * Required for PostgreSQL, integration tests, and local service dependencies.
  * Ensure the current user can access Docker configuration and the Docker daemon.

* PostgreSQL 14
  * Required as the project database.
  * Development may use PostgreSQL in Docker.
  * Install `psql` locally or expose it through the PostgreSQL Docker image for manual database inspection.

* Node.js and npm
  * Required for frontend development and tests.
  * Prefer an active LTS version for day-to-day development unless the project explicitly pins another version.
  * On this Windows host, use `npm.cmd` from PowerShell if `npm` fails because `npm.ps1` is blocked by execution policy.
  * Commit `package-lock.json` so CI and local installs use the same resolved dependency graph.

* ripgrep (`rg`)
  * Required for fast code and file searches by coding agents.

* GitHub Actions-compatible command-line tools
  * Local commands should mirror CI commands where possible.
  * Required checks are expected to include backend tests, frontend tests, frontend linting, frontend build, and frontend audit.

### Recommended

* IDE with Java 17, Spring Boot, React, and Maven support.
* HTTP client tooling such as Postman, Insomnia, or VS Code REST Client.
* OpenAPI viewer/editor tooling for validating generated API documentation.
* A PDF viewer for SeasonReport export verification.
* Browser dev tools in Chrome, Firefox, or Edge.

## Backend Frameworks and Libraries

Backend dependencies should be declared in `backend/pom.xml`.

Use Java 17, Spring Boot 3.2, and Spring Framework 6. Do not add alternative backend frameworks without explicit justification.

Current backend dependency baseline:

* Parent BOM: `org.springframework.boot:spring-boot-starter-parent:3.2.12`
  * Provides managed Spring Boot 3.2 dependency versions.

* Java compiler target: `17`
  * `maven.compiler.release` is set to `17`.

* `org.springframework.boot:spring-boot-starter-web`
  * Spring MVC REST APIs, embedded servlet runtime, and JSON HTTP handling.

* `org.springframework.boot:spring-boot-starter-validation`
  * Jakarta Bean Validation support for request DTOs and configuration validation.

* `org.springframework.boot:spring-boot-starter-security`
  * Authentication and RBAC authorization support at API boundaries.

* `org.springframework.boot:spring-boot-starter-data-jpa`
  * JPA repositories, entity mapping, and transaction integration for PostgreSQL persistence.

* `org.springframework.boot:spring-boot-starter-actuator`
  * Health and operational endpoints.

* `org.flywaydb:flyway-core`
  * Versioned database migrations under `backend/src/main/resources/db/migration`.

* `org.postgresql:postgresql`
  * Runtime PostgreSQL JDBC driver.

* `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0`
  * OpenAPI 3 documentation and Swagger UI for Spring MVC APIs.

* `org.testcontainers:testcontainers-bom:1.20.4`
  * Manages Testcontainers dependency versions.

* `org.apache.maven.plugins:maven-enforcer-plugin:3.5.0`
  * Enforces a minimum Java version of 17 during Maven builds.

* `org.springframework.boot:spring-boot-maven-plugin`
  * Spring Boot packaging and run support.

Expected backend dependencies:

* `spring-boot-starter-web`
  * REST API controllers and JSON request/response handling.

* `spring-boot-starter-validation`
  * Bean Validation for API input validation.

* `spring-boot-starter-security`
  * Authentication and RBAC authorization at API boundaries.

* `spring-boot-starter-data-jpa`
  * PostgreSQL persistence through repositories and transactions.

* PostgreSQL JDBC driver
  * Runtime database connectivity.

* Database migration tool
  * Use Flyway or Liquibase for schema migrations.
  * Keep migration files versioned under backend resources.

* OpenAPI support
  * Use Springdoc OpenAPI for OpenAPI 3.0 documentation.

* Jackson
  * JSON serialization. This is normally included by Spring Boot web dependencies.

* Spring Boot Actuator
  * Optional but recommended for health checks and operational visibility.

### Backend Test Dependencies

Backend tests must support Red-Green-Refactor TDD.

Current backend test dependency baseline:

* `org.springframework.boot:spring-boot-starter-test`
  * Includes JUnit Jupiter, Spring Test, AssertJ, Mockito, Hamcrest, JSONassert, and related test utilities managed by Spring Boot.

* `org.springframework.security:spring-security-test`
  * Security test helpers for authentication, authorization, and secured controller tests.

* `org.testcontainers:junit-jupiter`
  * JUnit 5 integration for container-based integration tests.

* `org.testcontainers:postgresql`
  * PostgreSQL containers for persistence integration tests.

* `io.rest-assured:rest-assured:5.5.0`
  * REST API integration and contract-style assertions.

Expected test dependencies:

* `spring-boot-starter-test`
  * JUnit 5, Spring Test, AssertJ, Mockito, and JSON/path test support.

* JUnit Jupiter
  * Unit and integration test runner.

* AssertJ
  * Fluent assertions for domain and service tests.

* Mockito
  * Mocking interfaces for external integrations and boundary services.

* Spring Security Test
  * Authentication and authorization tests for secured endpoints.

* Testcontainers
  * PostgreSQL integration tests against a real database container.

* REST Assured or Spring MockMvc/WebTestClient
  * API contract and controller tests.

Backend tests should be split by responsibility:

* Domain unit tests for lifecycle rules and business invariants.
* Application service tests for use-case orchestration.
* Repository or persistence integration tests for PostgreSQL mappings.
* API integration tests for REST, validation, and RBAC behavior.

## Frontend Frameworks and Libraries

Frontend dependencies should be declared in `frontend/package.json`.

Use React 18, JavaScript ES6+, HTML5, and CSS3. Do not introduce TypeScript or a different frontend framework without explicit justification.

Current frontend runtime dependency baseline:

* `react:18.3.1`
  * React component model.

* `react-dom:18.3.1`
  * Browser DOM rendering for React.

* `react-router-dom:6.30.4`
  * Client-side routing for page navigation.

* `vite:^8.0.16`
  * Frontend dev server, build tool, and test transform foundation.

* `@vitejs/plugin-react:^6.0.2`
  * Vite integration for React.

* `@rolldown/plugin-babel:^0.2.2`
  * Peer dependency required by the Vite 8 React plugin.

* `babel-plugin-react-compiler:^1.0.0`
  * Peer dependency required by the Vite 8 React plugin.

Expected frontend dependencies:

* React 18
  * UI component model.

* React DOM
  * Browser rendering.

* Frontend build tool
  * Vite is used for the React 18 JavaScript project because it is lightweight and widely supported.
  * The scaffold currently uses Vite 8 because npm audit reported unresolved advisories in older Vite lines.
  * Keep `@vitejs/plugin-react` and its peer dependencies aligned with the selected Vite major version.
  * If another build tool is selected, document the reason.

* React Router
  * Client-side routing for Dashboard, SeasonPlan details, task list, incident journal, ClimateMetric monitoring, AuditLog viewer, and SeasonReport screens.

* API client
  * Use the browser `fetch` API or a small client wrapper around it.
  * Avoid duplicating backend business rules in the frontend.

* Form and validation support
  * Native form handling is acceptable for early slices.
  * Add a form library only if it reduces real complexity.

* Charting library
  * Needed for graphical ClimateMetric visualizations.
  * Select one library when the ClimateMetric UI slice is implemented.

* PDF/export support
  * Needed for SeasonReport PDF export.
  * Select the implementation when the report export slice is implemented.

### Frontend Test Dependencies

Current frontend development and test dependency baseline:

* `vitest:4.1.8`
  * JavaScript unit and component test runner.

* `jsdom:25.0.1`
  * Browser-like DOM environment for component tests.

* `@testing-library/react:16.1.0`
  * React component tests from the user's perspective.

* `@testing-library/jest-dom:6.6.3`
  * DOM-specific test matchers.

* `@testing-library/user-event:14.5.2`
  * User interaction simulation for tests.

* `msw:2.6.6`
  * API mocking for frontend tests.

* `eslint:9.39.4`
  * JavaScript linting engine.

* `@eslint/js:9.39.4`
  * ESLint JavaScript recommended rules.

* `eslint-plugin-react:7.37.2`
  * React lint rules.

* `eslint-plugin-react-hooks:5.0.0`
  * React Hooks lint rules.

* `eslint-plugin-react-refresh:0.4.14`
  * React Fast Refresh export safety rules.

* `globals:15.12.0`
  * Browser, Node, and Vitest global definitions for ESLint flat config.

Expected test dependencies:

* Vitest or Jest
  * Unit test runner.

* React Testing Library
  * Component behavior tests from the user's perspective.

* `@testing-library/user-event`
  * Interaction tests for forms and workflows.

* Mock Service Worker
  * API mocking for frontend integration-style tests.

* ESLint
  * JavaScript linting.

* npm audit
  * Dependency vulnerability checks.
  * Use `npm.cmd audit --audit-level=moderate` from PowerShell.

Frontend tests should cover:

* Required field validation messages.
* Status-based field locking.
* Role-dependent command availability.
* API error rendering.
* Filtering and searching behavior.

## Local Services

Development should be able to run these services locally:

* PostgreSQL 14
  * Required for backend integration tests and local API runs.

* Mock Pollen Tree service
  * Temporary implementation behind an interface until the real integration is available.

* Mock Natural Calendar service
  * Temporary implementation behind an interface for season window validation.

* Mock Metric Sensors service
  * Temporary REST or WebSocket implementation when ClimateMetric work begins.

Temporary services must be deterministic, testable, and replaceable by production adapters without changing domain contracts.

## PowerShell Notes

Use PowerShell-compatible commands in project documentation and scripts.

Examples:

```powershell
java -version
mvn -version
npm.cmd --version
docker --version
rg --version
```

Install frontend dependencies:

```powershell
Set-Location X:\projects\seasonPlan\frontend
npm.cmd install
```

Run frontend checks:

```powershell
Set-Location X:\projects\seasonPlan\frontend
npm.cmd test -- --run
npm.cmd run lint
npm.cmd run build
npm.cmd audit --audit-level=moderate
```

Run backend checks:

```powershell
Set-Location X:\projects\seasonPlan\backend
mvn test
```

Start local PostgreSQL and apply Flyway migrations:

```powershell
Set-Location X:\projects\seasonPlan
docker compose up -d postgres flyway
```

If npm scripts are needed from PowerShell, prefer:

```powershell
npm.cmd install
npm.cmd test
npm.cmd run dev
```

Avoid assuming Bash syntax such as `export VAR=value`, `VAR=value command`, `&&`-chained examples, or Unix-only utilities in setup instructions.


## Backend Package Guidance

Each domain module should keep business logic independent from Spring, database, and external integrations.

Recommended package roles:

* `domain`
  * Entities, value objects, domain services, status transitions, business rules, and domain exceptions.

* `application`
  * Use-case services such as `SeasonPlanService`, command objects, result DTOs, and ports/interfaces.

* `adapter.in.rest`
  * Spring MVC controllers, request/response DTOs, validation mapping, and security boundary checks.

* `adapter.out.persistence`
  * JPA entities, Spring Data repositories, and persistence mappers.

* `adapter.out.integration`
  * Pollen Tree, Natural Calendar, and Metric Sensor clients.

* `shared`
  * Cross-cutting support that does not belong to a single domain module.

## Frontend Directory Guidance

Frontend code should be grouped by user-facing feature while keeping backend communication centralized.

Recommended roles:

* `api`
  * Fetch wrapper, generated or hand-written API clients, and error handling.

* `routes`
  * Route definitions and page-level composition.

* `components`
  * Reusable UI components without feature-specific business assumptions.

* `features`
  * Screens and workflow components for Dashboard, SeasonPlan details, task list, incident journal, ClimateMetric monitoring, AuditLog viewer, and SeasonReport viewer/export.

* `test`
  * Shared frontend test utilities and API mocks.

* `styles`
  * Global styles and design tokens.

## Initial Vertical Slice Focus

The first implementation slice should focus on:

* SeasonPlan draft creation and validation.
* DRAFT to SUBMITTED transition.
* SUBMITTED to APPROVED or returned status.
* APPROVED preparation through PollenReservation, MagicTask, FairyAssignment, readiness checklist, and RITUAL_READY transition.
* Immutable AuditLog entries for critical actions.
* RBAC checks for every command.

Do not implement later use cases until required:

* RitualSession launch.
* Active monitoring.
* Incidents and correction actions.
* Completion.
* Reports.
* Archive.
