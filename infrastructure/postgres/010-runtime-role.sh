#!/bin/sh
# Runs inside PostgreSQL, not in the Windows host shell. Fresh volumes only.
set -eu
: "${POSTGRES_USER:?initialization owner is required}"
: "${POSTGRES_DB:?database name is required}"
: "${DB_RUNTIME_USER:?restricted runtime login is required}"
: "${DB_RUNTIME_PASSWORD:?runtime password is required}"
if [ "$DB_RUNTIME_USER" = "$POSTGRES_USER" ] || [ "$DB_RUNTIME_USER" = "seasonplan_runtime" ]; then
    echo 'Runtime login must differ from the initialization owner and the NOLOGIN grant role.' >&2
    exit 1
fi
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 \
    --set=runtime_user="$DB_RUNTIME_USER" --set=runtime_password="$DB_RUNTIME_PASSWORD" <<'SQL'
do $$ begin
    if not exists (select 1 from pg_roles where rolname = 'seasonplan_runtime') then
        create role seasonplan_runtime nologin nosuperuser nocreatedb nocreaterole noreplication nobypassrls;
    end if;
end $$;
create role :"runtime_user" login nosuperuser nocreatedb nocreaterole noreplication nobypassrls password :'runtime_password';
grant seasonplan_runtime to :"runtime_user";
SQL
