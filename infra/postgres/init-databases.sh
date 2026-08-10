#!/bin/sh
set -eu

psql \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set ON_ERROR_STOP=1 \
  --set betufu_db_name="$BETUFU_DB_NAME" \
  --set betufu_db_user="$BETUFU_DB_USER" \
  --set betufu_db_password="$BETUFU_DB_PASSWORD" \
  --set keycloak_db_name="$KEYCLOAK_DB_NAME" \
  --set keycloak_db_user="$KEYCLOAK_DB_USER" \
  --set keycloak_db_password="$KEYCLOAK_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN', :'betufu_db_user')
WHERE NOT EXISTS (
  SELECT 1 FROM pg_roles WHERE rolname = :'betufu_db_user'
)
\gexec

SELECT format('CREATE ROLE %I LOGIN', :'keycloak_db_user')
WHERE NOT EXISTS (
  SELECT 1 FROM pg_roles WHERE rolname = :'keycloak_db_user'
)
\gexec

SELECT format(
  'ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L',
  :'betufu_db_user',
  :'betufu_db_password'
)
\gexec

SELECT format(
  'ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L',
  :'keycloak_db_user',
  :'keycloak_db_password'
)
\gexec

SELECT format('REVOKE %I FROM %I', granted_role.rolname, member_role.rolname)
FROM pg_auth_members AS membership
JOIN pg_roles AS granted_role ON granted_role.oid = membership.roleid
JOIN pg_roles AS member_role ON member_role.oid = membership.member
WHERE member_role.rolname IN (:'betufu_db_user', :'keycloak_db_user')
\gexec

SELECT format(
  'CREATE DATABASE %I OWNER %I',
  :'betufu_db_name',
  :'betufu_db_user'
)
WHERE NOT EXISTS (
  SELECT 1 FROM pg_database WHERE datname = :'betufu_db_name'
)
\gexec

SELECT format(
  'CREATE DATABASE %I OWNER %I',
  :'keycloak_db_name',
  :'keycloak_db_user'
)
WHERE NOT EXISTS (
  SELECT 1 FROM pg_database WHERE datname = :'keycloak_db_name'
)
\gexec

SELECT format('ALTER DATABASE %I OWNER TO %I', :'betufu_db_name', :'betufu_db_user')
\gexec

SELECT format('ALTER DATABASE %I OWNER TO %I', :'keycloak_db_name', :'keycloak_db_user')
\gexec

SELECT format(
  'REVOKE CONNECT, TEMPORARY ON DATABASE %I FROM PUBLIC',
  database_name
)
FROM (
  VALUES
    (:'betufu_db_name'),
    (:'keycloak_db_name'),
    ('postgres'),
    ('template1')
) AS contracted_database(database_name)
\gexec

SELECT format(
  'REVOKE ALL PRIVILEGES ON DATABASE %I FROM %I',
  database_under_test.datname,
  expected_access.role_name
)
FROM (
  VALUES
    (:'betufu_db_user', :'betufu_db_name'),
    (:'keycloak_db_user', :'keycloak_db_name')
) AS expected_access(role_name, database_name)
CROSS JOIN pg_database AS database_under_test
WHERE database_under_test.datallowconn
  AND database_under_test.datname <> expected_access.database_name
\gexec

SELECT format(
  'GRANT CREATE, CONNECT, TEMPORARY ON DATABASE %I TO %I',
  :'betufu_db_name',
  :'betufu_db_user'
)
\gexec

SELECT format(
  'GRANT CREATE, CONNECT, TEMPORARY ON DATABASE %I TO %I',
  :'keycloak_db_name',
  :'keycloak_db_user'
)
\gexec
SQL
