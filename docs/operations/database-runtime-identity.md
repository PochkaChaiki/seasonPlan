# Database identities and audit protection

## Identities

Use three distinct roles:

- Initialization/migration owner: owns the database schema and migration history. It can change schema and audit guards. Do not use it for application requests.
- `seasonplan_runtime`: a fixed, unprivileged **NOLOGIN** grant role. It must own no objects and have no parent roles.
- Runtime login: normally `seasonplan_app`. It has no elevated role flags, owns no objects, and belongs only to `seasonplan_runtime`.

Migration 015 gives the grant role SELECT/INSERT on audit records. It gives SELECT/INSERT/UPDATE/DELETE on an explicit list of business/session/outbox tables. It grants neither TRUNCATE nor migration-history access. Later migrations must grant each new table's required permissions explicitly. Do not use broad default grants that would also permit audit mutation.

The migration also removes PUBLIC schema CREATE and database TEMPORARY/CREATE permissions. Temporary tables could otherwise shadow unqualified audit/persistence table names. This affects other users of the same database. Use a dedicated application database. The grant role is cluster-wide; do not reuse it across deployments that require isolation.

The migration owner must have authority to apply these database/schema grants. A DBA must pre-create the NOLOGIN role if the migration account lacks CREATEROLE. Migration 015 stops if that role has elevated flags, parent roles or ownership. Never grant the migration-owner role to the runtime login.

## Fresh local Compose database

Use `.env.example` as the variable reference. Set separate owner and runtime passwords. `POSTGRES_USER`/`POSTGRES_PASSWORD` initialize the database and configure Liquibase only. `DB_RUNTIME_USER`/`DB_RUNTIME_PASSWORD` configure the application pool.

The mounted `infrastructure/postgres/010-runtime-role.sh` runs **inside PostgreSQL** on a fresh volume. It provisions the restricted login and grant role before Liquibase runs. It rejects owner-login reuse and stops on errors. Passwords come from environment variables and are not printed. The script is deliberately not an existing-role/password migration tool.

From Windows PowerShell:

```powershell
docker compose up --build
```

Do not print `docker compose config` with real credentials when collecting diagnostics.

## Existing database or volume

Initialization scripts do not run again for existing volumes. Do not remove a volume or recreate business data to install this change.

1. Take a backup and verify the restore procedure. Stop old backend instances before changing credentials.
2. Keep the existing database and initialization-owner names. Changing `POSTGRES_USER` in an environment file does not rename an existing database role.
3. As the DBA, inspect existing role flags, memberships and object ownership. Provision the NOLOGIN grant role if absent. Create a new, distinct runtime login with NOSUPERUSER, NOCREATEDB, NOCREATEROLE, NOREPLICATION and NOBYPASSRLS. Grant it only `seasonplan_runtime`. Set its password through the approved secret-management procedure. Do not grant ownership or a parent role with wider privileges.
4. Configure the runtime credentials and separate migration credentials. Start the new backend so Liquibase applies migration 015 as the existing schema owner. Review the database-wide PUBLIC grant changes first if the database is shared.
5. Connect with the runtime credentials. Check `current_user`, role flags and membership. Verify normal persistence and audit INSERT/SELECT. Verify that audit UPDATE/DELETE/TRUNCATE, trigger/function changes, `SET ROLE` to the owner, replication-role changes, schema creation and temporary-table shadowing are denied.
6. Verify sign-in, CSRF, account changes and the affected business commands. Remove obsolete application access to the old elevated credentials after the controlled rollout. Do not delete users, audit records or business data.

For non-Compose deployments, set `DB_RUNTIME_USER`, `DB_RUNTIME_PASSWORD`, `DB_MIGRATION_USER` and `DB_MIGRATION_PASSWORD` through the deployment secret store. The runtime password has no development fallback. Liquibase credentials are separate from the request datasource; protect both secrets. A DBA can provision roles directly on the FreeBSD target. The shell initialization script is a container bootstrap, not a host PowerShell script.

## Evidence and limits

`RuntimeDatabasePrivilegesTest` runs on PostgreSQL 14. It checks legitimate plan persistence/audit append, restricted identity flags and ownership, and SQLSTATE 42501 for forbidden mutation, DDL, escalation and shadowing. It also executes the supplied bootstrap script and checks its provisioned login. `PostgresAccountAdministrationTest` starts the real Spring application with separate runtime/Liquibase identities and exercises account persistence.

These controls protect the restricted SQL identity. They do not make audit records immutable to a privileged DBA, protect compromised migration credentials, implement durable external-operation recovery, or establish zero data loss. Existing production volumes and native FreeBSD deployment still require operator verification.
