# Database foundation: Part 2b

Prepared 14 October 2026; hosted evidence received 15 October. Tested on
disposable PostgreSQL 17.7. User-provided screenshots show successful V1 and all
five expected tables, plus four denied runtime permissions. User also reported
a successful application-role connection check. No independent cloud query run.
The user reported a successful Supabase connection in Part 2a. Login, Request
tables, demo seeding and production account bootstrap remain later work.

## Foundation supplied

The private `facilityflow` schema contains `user_account`, `audit_event`,
`spring_session`, `spring_session_attributes` and Flyway history. Account fields
include UUID, case-insensitive unique username, role, password hash, active state,
session/version counters and timezone-aware timestamps. Audit actors have foreign
keys and indexed lookup fields. Session attribute deletion cascades with sessions.
These support REQ-SH-001–007/014 and TECHNICAL-SPEC; they do not complete them.

`facilityflow_migrator` owns the schema; `facilityflow_app` has explicit table
permissions. Runtime cannot delete Accounts, modify/delete audits, create schema
objects or access Flyway history. Private-schema RLS policies allow only the
server role. These are database identities, not human product roles; service-level
authorization and safe audit-content validation still need implementation.

Migration is opt-in through `database-migrate`. Normal startup and `database-check`
remain free of migration writes. Flyway clean/baselining are disabled; unknown
newer migrations fail validation. SQL auto-initialization remains disabled.

## 1. Provision roles in the development project

In Supabase **SQL Editor**, paste and run
[bootstrap-roles.sql](../workflow/database/bootstrap-roles.sql) as the database
owner. This is administrative role provisioning, not application schema creation.
It creates two non-login roles and grants scoped provisioning/connect privileges.
Existing role names cause transaction rollback; inspect them instead of deleting
or silently modifying them. Keep the Data API disabled.

Give the roles different strong passwords and enable login. With an installed
`psql` client connected as the owner, use masked prompts:

```text
\password facilityflow_migrator
\password facilityflow_app
ALTER ROLE facilityflow_migrator LOGIN;
ALTER ROLE facilityflow_app LOGIN;
```

Alternatively, use private SQL Editor queries with local substitutions:

```sql
ALTER ROLE facilityflow_migrator LOGIN PASSWORD 'REPLACE_WITH_MIGRATION_PASSWORD';
ALTER ROLE facilityflow_app LOGIN PASSWORD 'REPLACE_WITH_DIFFERENT_APP_PASSWORD';
```

Do not run placeholders literally or store password-bearing queries in shared
queries, source, logs or screenshots. Retain passwords in your password manager.
Double any apostrophe inside a SQL literal; `psql` prompts avoid quoting issues.
Do not share passwords in chat. [Supabase roles](https://supabase.com/docs/guides/database/postgres/roles)

## 2. Apply Flyway with migration credentials

Retain the development JDBC URL and certificate path in the Part 2a terminal.
Replace `YOUR_PROJECT_REF`; shared pooler usernames use `role.PROJECT_REF`.
[Connection documentation](https://supabase.com/docs/guides/database/connecting-to-postgres)

```powershell
$env:SPRING_DATASOURCE_USERNAME = 'facilityflow_migrator.YOUR_PROJECT_REF'
$env:SPRING_DATASOURCE_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host 'Migration role password' -AsSecureString)).Password
.\gradlew.bat test bootJar
java -jar build/libs/facilityflow-ai-0.1.0-SNAPSHOT.jar --spring.profiles.active=database-migrate
Remove-Item Env:\SPRING_DATASOURCE_PASSWORD
```

Expected: Flyway migrates successfully to **version 1**, then normal exit.
Rerunning validates history and reports no pending migrations. Use the session
pooler for this command. Do not substitute `postgres` or the app role: ownership
and privileges are intentionally based on the migration identity. All application
schema changes go through versioned Flyway scripts.

After application, never edit V1, clean the database, delete history or baseline
around a failure. Subsequent changes require a new migration. The migration role
has no role/database administration privileges and must not be app runtime identity.

## 3. Switch to runtime and check

```powershell
$env:SPRING_DATASOURCE_USERNAME = 'facilityflow_app.YOUR_PROJECT_REF'
$env:SPRING_DATASOURCE_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host 'Application role password' -AsSecureString)).Password
java -jar build/libs/facilityflow-ai-0.1.0-SNAPSHOT.jar --spring.profiles.active=database-check
Remove-Item Env:\SPRING_DATASOURCE_PASSWORD
```

Expected: **Database connection verified.** Future app launches use this app role.
Keep migration credentials out of runtime configuration.

In Supabase SQL Editor verify metadata without showing personal data or hashes:

```sql
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'facilityflow' ORDER BY table_name;

SELECT version, description, success FROM facilityflow.flyway_schema_history
ORDER BY installed_rank;

SELECT
    has_table_privilege('facilityflow_app', 'facilityflow.user_account', 'DELETE') AS account_delete,
    has_table_privilege('facilityflow_app', 'facilityflow.audit_event', 'UPDATE') AS audit_update,
    has_table_privilege('facilityflow_app', 'facilityflow.audit_event', 'DELETE') AS audit_delete,
    has_schema_privilege('facilityflow_app', 'facilityflow', 'CREATE') AS runtime_ddl;
```

Expect the five tables listed above, successful version 1, and **false** for all
four permission flags. Flyway may also record schema creation with a null version.
Accounts are not seeded yet. Report only success or safe error categories.

## Tests and next work

Tests use a test-only embedded PostgreSQL dependency; no Docker/local Postgres
install is required and hosted database variables are never used. They verify
exact SQLSTATEs for constraints/permission failures, RLS denial, transaction
rollback, session deletion, migration reruns/newer-version rejection and the
actual Spring migration profile. This is not hosted-permission or auth evidence.

Next: Request/category/update/work-log/completion schema and grants, coordinated
with the persistence owner; then identity services, audited initialization and
Spring Security/Session integration. No browser login exists yet.

Acknowledgement: session DDL adapted from Spring Session JDBC 4.1.1
`schema-postgresql.sql` (Apache-2.0). Schema qualification/security is new MP3 work.
[Upstream source](https://github.com/spring-projects/spring-session/blob/4.1.1/spring-session-jdbc/src/main/resources/org/springframework/session/jdbc/schema-postgresql.sql)
The upstream license is retained in [Spring-Session-LICENSE.txt](licenses/Spring-Session-LICENSE.txt).
