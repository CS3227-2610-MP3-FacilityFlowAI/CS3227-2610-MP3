# Supabase PostgreSQL setup

The latest decision, [ADR-008](../workflow/decisions/ADR-008-separate-production-database.md),
requires separate databases. Keep the existing `facilityflow-dev` for development
and Preview; create fresh `facilityflow_prod` in the same Supabase project using
[ProductionDatabaseSetup](ProductionDatabaseSetup.md). Do not reprovision or reseed
the existing development database. Hosted isolation remains pending cutover.

Part 2a, 14 October 2026. Supabase JDBC configuration is implemented;
project provisioning and a live connection check are pending user setup.
Update: the user reported the connection succeeded. Part 2b adds the tested
foundation; use [DatabaseMigrations](DatabaseMigrations.md) for restricted roles
and Flyway application. Hosted migration remains unverified.
Authentication and Requester operations remain unimplemented. V1 application is
evidenced by the user's screenshots; [DatabaseCompletion](DatabaseCompletion.md)
supplies the V2/V3 upgrade and explicit first-account initialization steps.

## 1. Create the development project

In the [Supabase Dashboard](https://supabase.com/dashboard), create a project
named `facilityflow-dev`. Choose a region near the team (Singapore if available)
and save a strong database password in your password manager. Wait for readiness.
This project will hold synthetic development data only.

Before release, create a separate `facilityflow_prod` database in the same project with different
credentials. Preview/local configuration must never point to production
(NFR-DEP-001). Do not provision production access for development agents.

Use Supabase as the PostgreSQL host. FacilityFlow accounts will use the approved
Spring Security/Spring Session design. Supabase Auth users, API keys, Storage and
Realtime are not needed for this JDBC connection.

## 2. Set the dashboard controls

- Turn off **Enable Data API** in the Data API integration settings; FacilityFlow
  uses server-side JDBC. See [Supabase's Data API security guide](https://supabase.com/docs/guides/api/securing-your-api).
- In Database settings, enable SSL enforcement and download the root certificate.
  Save it locally, for example `C:/Users/your-name/.postgresql/supabase-dev.crt`.
  The driver is configured with `verify-full` and must trust the downloaded CA.
  See [SSL enforcement](https://supabase.com/docs/guides/platform/ssl-enforcement)
  and [pgJDBC SSL verification](https://jdbc.postgresql.org/documentation/ssl/).
- Leave FacilityFlow tables empty. Subsequent versioned Flyway migrations will
  create them; do not create Accounts or Requests manually in Table/SQL Editor.

The current read-only bootstrap check may use the project's database owner.
Before implementing writes, coordinate restricted application credentials and
separate migration credentials with the persistence owner. Do not deploy the
application using the privileged `postgres` owner as its runtime identity.
Account ownership checks still belong in server-side use cases; Data API controls
do not authorize JDBC operations (REQ-SH-002, NFR-SEC-001).

## 3. Copy connection details

Open **Connect**, choose **Session pooler**, and copy its actual host, port,
database and username. Session mode supports IPv4 and prepared statements.
Copy the pooler host from the dashboard rather than constructing it from region.
See [Supabase connection methods](https://supabase.com/docs/guides/database/connecting-to-postgres).

For the owner connection, the username is normally `postgres.PROJECT_REF` and
the port is `5432`. Use whatever the dialog actually shows. The database password
is distinct from your Supabase website login password and any API key.

Build a JDBC URL containing host/port/database only:

```text
jdbc:postgresql://YOUR_SESSION_POOLER_HOST:5432/postgres
```

Keep password and username in their separate environment variables. SSL mode and
certificate are configured separately in the app; do not add URL parameters that
override SSL verification. Never paste a password-bearing connection string in
chat, documentation, screenshots, source or logs (NFR-SEC-002).

## 4. Set process-local variables and run the check

From a PowerShell terminal in the repository root, replace the placeholders below
using the development project. Enter the password at the masked prompt:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://YOUR_SESSION_POOLER_HOST:5432/postgres'
$env:SPRING_DATASOURCE_USERNAME = 'postgres.YOUR_PROJECT_REF'
$env:SUPABASE_SSL_ROOT_CERT = 'C:/Users/your-name/.postgresql/supabase-dev.crt'
$env:SPRING_DATASOURCE_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host 'Development database password' -AsSecureString)).Password

.\gradlew.bat test bootJar
java -jar build/libs/facilityflow-ai-0.1.0-SNAPSHOT.jar --spring.main.web-application-type=none --spring.profiles.active=database-check

Remove-Item Env:\SPRING_DATASOURCE_PASSWORD
```

The terminal variables are inherited by the Java process. They are not written
to a file. Spring Boot does not automatically load `.env`; setting those values
only in `.env` will not configure this build. Closing the terminal removes its
process-local settings; IDE launches need their own environment configuration.

Expected success: **Database connection verified.**, then normal exit.
The check executes only `SELECT 1`; it neither creates tables nor changes data.
Failure exits unsuccessfully with safe guidance. Automated tests use synthetic
settings/mocks and do not connect to this project, even when these variables exist.

The ordinary startup without `database-check` does not prove database readiness:
Hikari opens its pool lazily. Use this explicit smoke command for Part 2a.
There is no HTTP server or browser interface yet.

## Troubleshooting and later release work

- Authentication/tenant errors: re-check the selected pooler's username, project
  reference, database password and host. Retype credentials privately.
- Certificate errors: check the downloaded development certificate and its full
  file path. Keep `verify-full`; do not bypass verification to make a check pass.
- Timeouts: confirm project readiness, network access and dashboard restrictions;
  use session pooling if direct IPv6 connectivity is unavailable.
- Later serverless deployment needs its own validated connection plan. Supabase
  transaction pooling requires disabling JDBC server-side prepared statements
  (`prepareThreshold=0`), and session-level operations/migration locks need review.
  Do not swap connection modes or run migrations blindly. The current two-connection
  application pool is for this development setup, not verified production sizing.
- Daily backup/restore evidence is still required by NFR-REL-002. Free-plan projects
  need an export/backup process; provider-managed daily backups are documented for
  paid plans. Coordinate this with the deployment owner before release.
  [Supabase backup documentation](https://supabase.com/docs/guides/platform/backups)

When setup is ready, report only whether the check succeeded. If it failed,
describe the error category without credentials or full connection dumps.
