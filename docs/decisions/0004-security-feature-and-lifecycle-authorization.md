# Security feature boundaries and lifecycle authorization

Status: Accepted. Refines the security/identity scope left unchanged by ADRs 0002
and 0003; their eight cohesive lifecycle service owners remain unchanged.

## Decision: keep account security separate, merge lifecycle policy with its owner

Do not move the entire security feature into lifecycle. Accounts, credential
verification/rotation, sessions, CSRF, authentication responses and development
bootstrap have a different responsibility from seasonal planning/execution. They
must not depend on SeasonPlan entities, lifecycle repositories or status workflows.

Instead, divide `com.seasonplan.security` by responsibility and expose a narrow,
framework-independent identity contract. Place lifecycle-specific permission and
resource-access decisions in `com.seasonplan.lifecycle.authorization`. This is the
useful part of the requested merge: lifecycle owns the rules for its operations,
while authentication and account administration stay independently organized.

## Ownership

| Package | Responsibility |
| --- | --- |
| `security.identity` | Public neutral ActorRole, CurrentActor and CurrentActorProvider contracts |
| `security.domain` | Account values and username rules, independent of Spring/JDBC |
| `security.service` | Local account application contract, validated command values, core service and required password/transaction contracts |
| `security.repository` | Account persistence contract, using neutral values |
| `security.exception` | Framework-independent account/access failures |
| `security.api` | Auth/admin HTTP endpoints, request/response mapping and security error translation |
| `security.infrastructure.jdbc` | Account SQL and database-backed authentication lookup |
| `security.infrastructure.spring` | Spring principal, current-actor mapping, auth handlers, password encoder and transaction adapters |
| `security.config` | Filter-chain/password configuration, application service composition and dev-only bootstrap |
| `lifecycle.authorization` | One lifecycle permission matrix and lifecycle resource-access policy |

The formerly misplaced `lifecycle.config.SecurityConfiguration` belongs to
`security.config`. Spring filter/session mechanics are not seasonal configuration.
There is no flat bucket of security Java classes, no generic `application/shared`
package, and no forwarding service retained solely to preserve old Java names.

## Identity and trust

The fixed seven-role business catalog moves from lifecycle into `security.identity`;
its names/database values are unchanged. `CurrentActor` and its provider move with
it, eliminating security's reverse dependency on lifecycle. Lifecycle may consume
these neutral contracts, never Spring Security principals or concrete adapters.

The system-administrator privilege stays separate from ActorRole. It does not
become an eighth business role or grant implicit planning/pollen/ritual privileges.
The neutral actor can carry that independently authenticated administrator flag
for account-service authorization. Three-argument actor construction defaults it
to false; role collections remain immutable.

The request path is:

```text
Spring authentication + persisted user/role data
  -> LocalUserDetails
  -> SecurityCurrentActorProvider
  -> framework-neutral CurrentActor
  -> API permission selection / account command
  -> application-service authorization and business rules
```

Actor IDs, assigned roles and administrator privilege come from the authenticated
server principal, never caller-selected identity headers or business request-body
fields. A public Java record is not cryptographic proof of authentication: its
source must be trusted.

Existing lifecycle core-only method signatures taking a selected role and actor
ID remain trusted internal application contracts in this bounded refactor. The
HTTP adapter binds both values to its current authenticated actor. This is not a
claim that an arbitrary in-process caller-supplied role independently authenticates
its caller. Future adapters must supply equally trusted identity context rather
than bypass authorization or accept role claims from their payloads.

## Account application/persistence boundary

LocalUserAdministration's commands belong to its contract, not its implementation.
The account application service no longer accepts Spring UserDetails, executes SQL,
or imports Spring transaction/password classes. It authorizes administrator account
creation and self-only credential rotation using neutral identity, persistence,
password hashing and transaction contracts.

Infrastructure preserves the users/local_credentials/user_roles atomic write
boundary using real Spring/JDBC transaction management; no permissive production
transaction fake is introduced. Password encodings retain the existing configured
encoder and BCrypt input limits. Public account HTTP DTOs, error codes, response
shapes and safe authentication failure messages remain compatible.

Credential rotation verifies the supplied current password against the currently
stored encoded credential for the authenticated UUID, not a stale session's hash.
A regression test precedes this correction. Verification and updates participate
in the account transaction. An incorrect/missing password changes nothing and
keeps the session; success changes only the actor's account and then invalidates
the current HTTP session. Request bodies cannot select another account ID.

Initial administrator creation stays restricted to the explicit dev bootstrap,
initial-only when an administrator already exists, without business roles or known
default credentials. This does not introduce registration, password reset, role
editing, administrator delegation or automatic global session revocation.

## Authorization policy and transport guards

Security authentication identifies the actor. The HTTP boundary enforces session,
CSRF and endpoint access; operation authorization must also execute at the
application-service boundary. Resource-sensitive rules stay next to lifecycle
operations, not inside a global authentication/filter class.

Lifecycle API role selection and service guards consult the same permission map.
Role checks leave ValidationSupport, which retains only field-error aggregation.
Permission duplication through independent controller role lists is removed;
Spring authentication guards and JSON 401/403 behavior remain in place.
Existing ownership rules for review history, report generation and correction
updates continue to execute at their existing application/transaction boundary.
Status transitions, resource prerequisites, audit immutability and report/pollen
rules remain domain/application behavior, not authorization policy.

### Source-backed readiness correction

ST-00's transition table authorizes the Talent Coordinator for APPROVED ->
RITUAL_READY. The HTTP endpoint already enforced that; the service additionally
accepted Pollen Guardian. A red regression test captures that mismatch, then both
boundaries use the coordinator-only operation permission. This closes the direct
service inconsistency without expanding HTTP access. Checklist-item completion
still permits the existing coordinator/pollen-guardian roles.

ST-00's operation matrix also restricts draft editing/submission to the plan's own
planner. The former service checked only the planner role. Separate red-first
regressions now require the loaded plan's creator to match the acting planner
before either mutation, inside the existing plan transaction. Foreign planners
cannot change draft fields/status or create audit/outbox effects. This implements
the documented ownership rule, not a new read/list visibility policy. No
undocumented permission expansion or new lifecycle state is introduced.

## Rollout: one-time reauthentication is explicitly accepted

The operator chose requiring sign-in again for sessions created before this
refactor, rather than adding legacy session compatibility classes. Spring Session
JDBC serializes the authenticated principal and role enum; their moved Java class
names cannot transparently load the old serialized snapshots. New authentication
snapshots have serialization round-trip coverage, but that does not establish
compatibility with old names.

Use a coordinated rollout: stop every old application instance, revoke the old
Spring Session entries as an explicit operator action, deploy the new code, then
require fresh authentication. Do not run mixed old/new instances against the same
session store during this rollout. The agent does not delete session rows, change
stored accounts, add a destructive migration, or introduce a hidden legacy shim.
Subsequent sessions use the stable new classes and retain the existing restart/
PostgreSQL-backed persistence design. The ST-01 operations note contains the
controlled session-only revocation procedure; no audit, account or pollen data is
removed.

## Verification and limits

Red-first architecture/account/authorization tests protect package ownership,
framework-independent contracts and core services, absence of security-to-lifecycle
imports, public identity allowlists, immutable identity roles, administrator/business
isolation, current-password verification, self-only updates, transaction failure
handling, session invalidation and dev-profile behavior. Permission tests cover
role combinations and forbidden API/direct-service calls before side effects.

Existing lifecycle, integration, audit/outbox and report tests remain in place.
JDK 17 is selected for builds. Real PostgreSQL rollback/concurrency checks require
a working Docker environment; unit transaction mocks are not proof of database
atomicity. Actual commands/results and remaining environment limitations are
recorded in `backend/CHANGELOG.md`.
