# Complete development database setup (V2-V6 and initialization)

Prepared 15 October 2026. V1 is already applied, as shown in the user's screenshots.
Update: the user reported successful hosted initialization with six Accounts,
six Requests, two Accounts per role and one Request per state. These are
user-reported outcomes; no independent hosted query was performed.
Keep Data API disabled and use the existing private
schema, certificate and restricted roles. Do not edit V1 or rerun role provisioning.

## Schema supplied for the team

| Migration | Tables / data |
| --- | --- |
| V1 (already applied) | user_account, audit_event, spring_session, spring_session_attributes, Flyway history |
| V2 | category (seven required initial categories), maintenance_request, requester_update, work_log, request_completion_history |
| V3 | skill_tag, technician_skill, ai_invocation, rate_limit_bucket (five initial global slots) |
| V4 | Require timestamps on new assignments while preserving existing migrated unknown times; no new tables |
| V5 | Shared AI gateway lifecycle/lease coordination |
| V6 | Append-only request_submission table for duplicate create rejection; read-only schema_version view for runtime compatibility checks |

After V3 there are **14 tables**, including Flyway history. UUID identities,
foreign keys, UTC-capable timestamps, catalogue uniqueness, Unicode character
limits and relevant lookup indexes are supplied. Display IDs are database-generated
FF-000001 through FF-999999; overflow fails and never wraps. Runtime cannot alter
Request owner/Recording Actor/IDs/creation times, hard-delete business history,
or overwrite work logs, updates, completions and AI invocation evidence.

After V6 there are **15 tables**, plus the `schema_version` view. Apply V5/V6 using
the explicit `database-migrate` profile and the migration identity, following the
existing setup commands below. Web startup now requires successful versions 1-6;
it performs only a compatibility read, never a migration. Runtime can SELECT the
version/success projection but cannot access full Flyway history or modify it.
The original step 2 recorded no hosted Requester migration. The user is now unsure
whether the old Requester V5 reached shared Supabase. Before upgrading, follow
the migration-history check in DeveloperGuide.md; do not repair history blindly.

All application tables have RLS restricted to the trusted JDBC server identity;
the schema is not exposed to browser Data API roles. Product role authorization,
active catalogue/Technician eligibility, named lifecycle transitions, Work Log
requirements, priority decisions and atomic audit writes still belong in use-case
services. The database checks valid shapes; it is not an authorization service.

AI invocation storage has typed metadata only, with nullable provider usage and
no raw prompt/output/advice fields. Rate-limit storage supports rolling call
timestamps, atomic version checks and expiring global slot leases. The gateway
must implement pruning, deadline-safe leases, claim/release and actual 5/minute,
30/hour and five-in-flight enforcement; the table alone does not enforce quotas.
Skill deactivation retains immutable tag records; services record historical
tag ID/label snapshots in safe structured audits where required.

## 1. Upgrade the development database

Open a PowerShell terminal at the repository root. Re-enter the development
settings if the previous terminal closed. Use the exact existing certificate path:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://YOUR_SESSION_POOLER_HOST:5432/postgres'
$env:SUPABASE_SSL_ROOT_CERT = 'C:/path/to/your/supabase-certificate.crt'
$env:SPRING_DATASOURCE_USERNAME = 'facilityflow_migrator.YOUR_PROJECT_REF'
$env:SPRING_DATASOURCE_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host 'Migration role password' -AsSecureString)).Password

.\gradlew.bat test bootJar
java -jar build/libs/facilityflow-ai-0.1.0-SNAPSHOT.jar --spring.main.web-application-type=none --spring.profiles.active=database-migrate
Remove-Item Env:\SPRING_DATASOURCE_PASSWORD
```

Run the Java command only after a successful build. Expected: V1 validates,
Pending V2-V4 apply successfully and the database reaches **version 4**. No Accounts or
demo Requests are created by Flyway. Repeating migration reports up-to-date.
Never clear Flyway history, edit applied scripts, baseline around errors or clean
the database to fix a failure. Report a safe error category if it fails.

## 2. Initialize development data explicitly

On this fresh **development** project, use the application role and enable demo
data only for the initialization command:

```powershell
$env:SPRING_DATASOURCE_USERNAME = 'facilityflow_app.YOUR_PROJECT_REF'
$env:SPRING_DATASOURCE_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host 'Application role password' -AsSecureString)).Password
$env:DEMO_DATA_ENABLED = 'true'
java -jar build/libs/facilityflow-ai-0.1.0-SNAPSHOT.jar --spring.main.web-application-type=none --spring.profiles.active=database-initialize,development
Remove-Item Env:\DEMO_DATA_ENABLED
Remove-Item Env:\SPRING_DATASOURCE_PASSWORD
```

Expected: **Database initialization complete.** This atomically adds requester1/2,
technician1/2 and manager1/2 with development-only password **Welcome123**, six
Requests covering all six states, work/completion evidence and Audit Events.
Hashes use independently salted PBKDF2-HMAC-SHA256 via Spring Security's v5.8
defaults (16-byte salt, 310,000 iterations, 256-bit output), with the delegating
`{pbkdf2@SpringSecurity_v5_8}` format. Login integration must verify that format.

These are synthetic fixture accounts, not production credentials. Ordinary startup
never initializes data: it requires the explicit `database-initialize` profile.
Existing Accounts make initialization a no-op; other pre-existing business data
with no Accounts makes it fail. A database advisory transaction lock serializes
concurrent initializers, and any failure rolls back all inserted business/audit rows.
Consumed display-number gaps after rollback are allowed. No rows are deleted/reseeded.

## 3. Verify in Supabase SQL Editor

Run these separately to view each result; they expose metadata and counts only:

```sql
SELECT version, description, success FROM facilityflow.flyway_schema_history
WHERE version IS NOT NULL ORDER BY installed_rank;
```

Expected after upgrading: successful versions **1, 2, 3, 4**.

```sql
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'facilityflow' ORDER BY table_name;
```

Expected: the **14 tables** listed above.

```sql
SELECT role, count(*) FROM facilityflow.user_account GROUP BY role ORDER BY role;
SELECT status, count(*) FROM facilityflow.maintenance_request GROUP BY status ORDER BY status;
```

For a fresh demo seed: two Accounts per role, one Request per state. Run the two
count queries separately if the editor shows only the last result. Do not display
password_hash or session attribute contents in screenshots/logs.

```sql
SELECT tablename FROM pg_tables
WHERE schemaname = 'facilityflow'
  AND tablename <> 'flyway_schema_history' AND NOT rowsecurity;
```

Expected: zero rows. Repeat the Part 2b runtime permission checks; they should
remain false. Finally run `database-check` with app credentials to verify connectivity.

## Production initialization

ADR-008 requires a fresh separate Production database in the existing project. Follow
[ProductionDatabaseSetup](ProductionDatabaseSetup.md) for the current V1-V6
migration, one-time Manager bootstrap and staged cutover sequence. Keep the
existing development database unchanged; do not copy its accounts or sessions.

Use a separate Production database and runtime credentials. Apply migrations with its migration
identity, then initialize with its app identity. Leave DEMO_DATA_ENABLED absent/false
and supply BOOTSTRAP_MANAGER_USERNAME, BOOTSTRAP_MANAGER_DISPLAY_NAME and
BOOTSTRAP_MANAGER_PASSWORD from deployment secrets. Password: 8–24 Unicode code
points; username: 3–32 permitted characters; display name: 2–80 after trimming.
The same initialization profile creates only one Manager and its Audit Event;
no demo Accounts or Requests. Remove/rotate the bootstrap password after success.
There is no ordinary self-registration endpoint.

Production deployment, backups/restore, session/cookie behavior, runtime schema
version validation and application role workflows remain separate release work.
Current runtime still has no HTTP endpoints; complete those controls before
exposing business operations. Database readiness does not establish product readiness.

## V4 assignment integrity upgrade (16 October 2026)

An existing V3 database uses the usual `database-migrate` command above with
migrator credentials. V1-V3 remain unchanged; do not reinitialize or reseed.

V4 preserves existing migrated assignments with unknown times (REQ-TECH-003).
Their metadata and progress may still change with the same Technician. New
assigned records, reassignment, and return/reopen to ASSIGNED require a timestamp;
a known timestamp cannot be cleared while retaining an assignment. A timestamp
without a Technician is rejected. No timestamps are invented or backfilled.

This follows the user's confirmed legacy policy. Hosted V4 has not been applied
or verified in this session. Investigate any existing timestamp-without-Technician
records against audit history if migration rejects them; do not clear history.

## Initializer safeguards (16 October review)

Demo initialization requires both `DEMO_DATA_ENABLED=true` and the explicit
`development` profile. A `production` or `prod` profile rejects demo seeding even
when combined with `development`. Production Manager bootstrap continues to use
only `database-initialize`, without demo mode. Profiles are operator-supplied
configuration. Under ADR-008, initialize only the new Production database in a
trusted operator environment; never enable either initializer profile on Vercel.

Initialization waits at most five seconds per lock acquisition using transaction-
local `lock_timeout`. On contention it fails and rolls back; retry after the other
initializer finishes. This setting does not alter pooled connection defaults.
Completion metadata/history remains available after return/reopen, as required
by REQ-MGR-007/008 and TECHNICAL-SPEC; lifecycle authorization remains server-side.
