# ST-01 Authentication and RBAC

The backend is the authorization boundary. Browser sessions are PostgreSQL-backed
Spring Session JDBC sessions; the React client uses only same-origin `/api` calls
with `credentials: include`. It never stores an authoritative identity, role, or
credential in browser storage.

`CurrentActor(UUID, username, roles)` is the provider-neutral identity passed
into the lifecycle boundary. The local adapter resolves it from Spring Security's
`LocalUserDetails`; business code does not receive `Authentication`, sessions,
JWT claims, or OIDC provider classes. A future Keycloak migration adds an
`external_identity_links(provider, issuer, subject, user_id)` table with unique
`(issuer, subject)` and `ON DELETE RESTRICT`, then replaces only the inbound
authentication adapter. The durable local user UUID, ownership values, audit
references, role policy, and `/api/auth/me` shape remain unchanged.

## Fixed role catalog

| Russian actor | Java/database role |
| --- | --- |
| Планировщик сезона | `FAIRY_PLANNER` |
| Главный хранитель | `CHIEF_GUARDIAN` |
| Координатор талантов | `TALENT_COORDINATOR` |
| Хранитель пыльцы | `POLLEN_GUARDIAN` |
| Балансировщик климата | `CLIMATE_BALANCER` |
| Контролёр магических аномалий | `MAGIC_ANOMALY_CONTROLLER` |
| Участник-фея | `FAIRY_PARTICIPANT` |

The `user_roles` database check constraint and `ActorRole` enum carry exactly
this catalog. There is no implicit hierarchy; a multi-role user receives the
union of explicitly assigned roles.

## Account administration

Account administration is deliberately separate from the seven SeasonPlan
business roles. The `users.system_admin` flag grants the Spring authority
`ROLE_SYSTEM_ADMIN`; it is not added to `ActorRole`, cannot be selected in the
registration form, and grants no implicit SeasonPlan permissions. This keeps
operational account management out of domain authorization and prevents an
administrator account from silently becoming a planner, guardian, or other
business actor.

There is no public registration endpoint. An authenticated system
administrator may create an enabled local user through `POST /api/admin/users`.
The request contains a normalized username, display name, plaintext password
used only for the current request, and one or more roles from the fixed catalog.
The backend validates and encodes the password before transactionally inserting
the user, local credential, and roles. The response never contains the password
or encoded credential. The slice intentionally does not add user listing,
deletion, disabling, role editing, password reset, or administrator delegation.
Local passwords must contain at least 12 characters and no more than 72 UTF-8
bytes, matching BCrypt's safe input boundary; both the browser and backend
enforce the rule, while the backend remains authoritative.

Every authenticated local user may change only their own username and password
through `PUT /api/auth/me/credentials`. The request must include the current
password; it contains no target user ID or administrator flag. A successful
change invalidates the current server session and requires authentication with
the new credentials. This prevents a stale session principal from retaining the
old username and gives credential rotation a clear security boundary.

## Development bootstrap administrator

The development bootstrap is selected only by the explicit Spring `dev`
profile. `DEV_ADMIN_USERNAME` and `DEV_ADMIN_PASSWORD` are required and
`DEV_ADMIN_DISPLAY_NAME` controls the presentation name. Docker Compose reads
these values from the untracked project `.env`; `.env.example` documents the
required keys without providing a production credential.

The bootstrap creates one `system_admin` account only when no administrator
exists. After the first successful start, PostgreSQL is authoritative, so a
self-service credential change is not overwritten by a restart. To apply
different `.env` bootstrap credentials to an existing development volume,
create a fresh development database explicitly. Missing or blank bootstrap
credentials fail startup under `dev`; the initializer does not exist in
non-development profiles, and no development default can authenticate in a
production-like profile.

For local use, edit the three `DEV_ADMIN_*` values in the project `.env`, run
`docker compose up --build`, and open `http://localhost:5173`. Port `5173`
serves the React login form; port `8080` remains the JSON backend API. On the
first start against a database without an administrator, use the configured
development credentials. After that, change them from **Profile and security**
in the application; editing `.env` does not overwrite an existing account.

## Operations

The browser administration form is available only to `ROLE_SYSTEM_ADMIN` and
the backend repeats this authorization at the API boundary. The React route
guard and hidden navigation link are usability measures, not security controls.
Never pass a password on the command line, log request bodies, commit `.env`, or
enable the development bootstrap outside the explicit `dev` profile.

At the FreeBSD edge Nginx terminates TLS 1.2/1.3 and proxies `/api/` to the
loopback Java service. It must remove `X-Actor-Id`, `X-Actor-Role`, and public
forwarded headers before setting its own trusted forwarded protocol/host values.
The Java service and PostgreSQL bind privately; Actuator runs on its separate
loopback management port. Verify after each release that `SESSION` is `Secure`,
`HttpOnly`, and `SameSite=Lax`, then test login, restart/session persistence,
logout, account disablement, and emergency database-backed session revocation.

CSRF is bootstrapped at `GET /api/auth/csrf`; login and logout require the token
in `X-XSRF-TOKEN`. Invalid credentials always return generic `401
INVALID_CREDENTIALS`; anonymous API requests return JSON `401`, and denial
returns JSON `403`. Passwords, raw CSRF values, and cookies must never enter
application logs.
