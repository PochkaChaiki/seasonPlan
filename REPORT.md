# Environment Setup Report

Date: 2026-06-06

## Summary

The project environment was set up as a client-server monorepo with a Spring Boot backend, React frontend, local PostgreSQL service definition, CI workflow, and supporting documentation.

No business use cases were implemented. The added code is limited to application/bootstrap scaffolding and smoke tests required to verify the environment.

## Files Added

Repository-level setup:

* `.editorconfig`
* `.gitignore`
* `.env.example`
* `docker-compose.yml`
* `.github/workflows/ci.yml`
* `REPORT.md`

Backend setup:

* `backend/pom.xml`
* `backend/src/main/java/com/seasonplan/SeasonPlanApplication.java`
* `backend/src/main/resources/application.yml`
* `backend/src/main/resources/application-test.yml`
* `backend/src/main/resources/db/migration/V1__initial_schema.sql`
* `backend/src/test/java/com/seasonplan/SeasonPlanApplicationTest.java`

Frontend setup:

* `frontend/package.json`
* `frontend/package-lock.json`
* `frontend/index.html`
* `frontend/vite.config.js`
* `frontend/eslint.config.js`
* `frontend/src/main.jsx`
* `frontend/src/App.jsx`
* `frontend/src/App.test.jsx`
* `frontend/src/styles/global.css`

Documentation structure:

* `docs/api/openapi.yaml`
* `docs/architecture/README.md`
* `docs/decisions/README.md`

## Files Updated

* `ENVIRONMENT_SETUP.md`
  * Added lockfile, CI/check, npm audit, PowerShell command, Docker Compose, and Vite 8 notes discovered during setup.

## Backend Setup

The backend is configured as a Maven Spring Boot 3.2 project targeting Java 17 bytecode.

Included dependencies:

* Spring Boot web, validation, security, data JPA, actuator
* PostgreSQL JDBC driver
* Flyway
* Springdoc OpenAPI
* Spring Boot test
* Spring Security test
* Testcontainers for PostgreSQL
* REST Assured
* Maven Enforcer minimum Java check

The current smoke test verifies that the application bootstrap class is available.

## Frontend Setup

The frontend is configured as a React 18 JavaScript project using Vite.

Included dependencies:

* React 18
* React DOM
* React Router
* Vite
* Vite React plugin
* Vitest
* React Testing Library
* Testing Library user-event
* Mock Service Worker
* ESLint

Vite was upgraded to version 8 during setup because npm audit reported unresolved moderate advisories for the older Vite line. The Vite React plugin and required peer dependencies were aligned with Vite 8.

## Local Services

`docker-compose.yml` defines PostgreSQL 14 with:

* Configurable database, user, password, and port through environment variables
* Persistent Docker volume
* Health check using `pg_isready`

`.env.example` documents expected local environment variables.

## Verification

The following checks were run successfully:

```powershell
Set-Location X:\projects\seasonPlan\backend
mvn test
```

Result: 1 backend test passed.

```powershell
Set-Location X:\projects\seasonPlan\frontend
npm.cmd test -- --run
npm.cmd run lint
npm.cmd run build
npm.cmd audit --audit-level=moderate
```

Results:

* 1 frontend test passed.
* ESLint completed with no errors.
* Production build completed successfully.
* npm audit reported 0 vulnerabilities.

## Notes

The first Maven and npm dependency resolution attempts failed or timed out under the sandbox because network access was restricted. The commands were rerun with approved network access and completed successfully.

The local host still has Java 24 active on `PATH`. The Maven project is configured to compile with Java release 17, but installing OpenJDK 17 and setting `JAVA_HOME` remains required for a fully aligned local development host.
