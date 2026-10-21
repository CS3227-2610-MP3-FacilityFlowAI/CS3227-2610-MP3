# Separate Production database setup

Implements NFR-DEP-001 under [ADR-008](../workflow/decisions/ADR-008-separate-production-database.md).
The user selected a fresh database with required accounts only. The Production
cutover was verified on 21 October 2026; the procedure below records its setup.
See [cutover evidence](../logs/2026-10-21-production-database-cutover.md) for
automated checks, user-reported browser checks and verification limits. Preview
configuration was corrected; a new Preview deployment smoke remains outstanding.

| Environment | Existing Supabase project | Database | Runtime role |
| --- | --- | --- | --- |
| Local / Preview | `facilityflow-dev` | Existing `postgres` | Existing `facilityflow_app` |
| Production | Same project | New `facilityflow_prod` | New `facilityflow_prod_app` |

The project/server, owner administration, compute and connection limits are shared.
Application records, sessions, runtime credentials and database permissions are
separate. The extra database is managed through PostgreSQL clients, not Supabase
Table Editor, Data API, Auth or Storage. JDBC is the application's only database
interface. No new Supabase project is needed.

## Create and provision

If no PostgreSQL client is installed, download the **Windows installer** from
[DBeaver Community](https://dbeaver.io/download/), run it with the default bundled
Java runtime, then open DBeaver. No local PostgreSQL server is needed. Create a
PostgreSQL connection using the existing project's Session pooler details and
verified TLS; allow DBeaver to download its PostgreSQL JDBC driver if prompted.
See [installation documentation](https://dbeaver.com/docs/dbeaver/Installation/).

1. Use a trusted PostgreSQL client such as `psql`, connected as the existing
   project's owner with verified TLS. Use the existing project's actual Session
   pooler host/port and project reference; first connect to database `postgres`.
   Supply the owner password privately through a masked prompt. If `psql` is not
   installed, install a PostgreSQL client or use a trusted database GUI. Do not
   change Vercel or local runtime credentials yet.
2. Execute [bootstrap-production-database.sql](../workflow/database/bootstrap-production-database.sql)
   with autocommit enabled and stop on errors. For psql, use
   `-v ON_ERROR_STOP=1 -f workflow/database/bootstrap-production-database.sql`.
   Do **not** wrap it in a transaction: PostgreSQL forbids CREATE DATABASE there.
   It creates distinct Production roles, an empty database from `template0`, and
   removes public connections to the new database. Existing names fail; inspect
   partial setup rather than dropping databases or roles. Do not rerun the
   original development `bootstrap-roles.sql` or change existing role passwords.
3. Set new, different Production passwords using masked psql prompts:

   ```text
   \password facilityflow_prod_migrator
   \password facilityflow_prod_app
   ALTER ROLE facilityflow_prod_migrator LOGIN;
   ALTER ROLE facilityflow_prod_app LOGIN;
   ```

   Never grant membership in development roles to the Production roles. PostgreSQL
   roles are cluster-wide, so membership could expose development data. Keep
   owner/migration credentials out of Vercel runtime.
4. Verify a new connection to database `facilityflow_prod` through the Session
   pooler as `facilityflow_prod_migrator.PROJECT_REF`. Check `current_database()`
   and `current_user` before migration. Preserve the current CA and verify-full
   TLS settings. If Supabase rejects the non-default database connection, stop;
   do not point Production at `postgres` as a workaround.

Do not create Supabase Auth users. FacilityFlow accounts use Spring Security.
Do not paste passwords or password-bearing URLs in chat.

## Migrate and initialize once

Use a separate trusted operator terminal with Production connection variables;
keep `application-local.yml` pointed at development. Build the approved release
JAR from its exact commit; do not initialize with an unrelated stale JAR. Use
`SPRING_CONFIG_LOCATION=optional:classpath:/` for these operator commands to
exclude external local configuration files, and supply the database settings
through the terminal environment. Remove that override after the operator work.

1. Set `SPRING_DATASOURCE_URL` to the new JDBC host/port/database URL,
   `SUPABASE_SSL_ROOT_CERT` to the verified local CA path,
   `SPRING_DATASOURCE_USERNAME=facilityflow_prod_migrator.PROJECT_REF`, and the
   migrator password using the existing masked prompt procedure.
2. Run the existing `database-migrate` command from
   [DatabaseMigrations](DatabaseMigrations.md). It applies **all migrations V1–V6**
   in the current release, not just the historical V1 example. Verify six
   successful versions in `facilityflow.flyway_schema_history`; never clean,
   baseline, repair or edit migration history to hide a failure.
3. While still connected to `facilityflow_prod` as its migrator, execute
   [production-runtime-permissions.sql](../workflow/database/production-runtime-permissions.sql).
   It applies the exact migration-defined table/column/sequence permissions and
   RLS policies to the new Production runtime role. Existing migration files stay
   unchanged. Reapply this script after every later Production migration before
   deployment; it refuses development databases and unexpected policy roles.
   Switch to `facilityflow_prod_app.PROJECT_REF` and its new password. Run the
   existing `database-check` command; require `Database connection verified.`
4. Set `DEMO_DATA_ENABLED=false` and privately supply
   `BOOTSTRAP_MANAGER_USERNAME`, `BOOTSTRAP_MANAGER_DISPLAY_NAME` and
   `BOOTSTRAP_MANAGER_PASSWORD`. Follow
   [Production initialization](DatabaseCompletion.md#production-initialization)
   and run:

   ```text
   java -jar build/libs/facilityflow-ai-0.1.0-SNAPSHOT.jar --spring.main.web-application-type=none --spring.profiles.active=database-initialize,production
   ```

5. Verify initialization created one Manager, no demo accounts and no maintenance
   requests. Remove bootstrap variables and database passwords from the operator
   environment when finished. Confirm runtime permission/RLS checks from the
   existing database guides; do not grant the runtime role schema administration.

## Configure and stage the cutover

1. In Vercel, split the three existing shared database variables by target.
   Preserve their current values for **Preview** (and Development if configured).
   Set independent **Production-only** values:

   | Variable | Production value |
   | --- | --- |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://EXISTING_POOLER_HOST:5432/facilityflow_prod` (use actual host/port) |
   | `SPRING_DATASOURCE_USERNAME` | `facilityflow_prod_app.PROJECT_REF` |
   | `SPRING_DATASOURCE_PASSWORD` | New Production **app-role** password |
   | `SUPABASE_SSL_ROOT_CERT` | Keep `/app/supabase-root-2021.crt`; same verified project CA |
   | `SPRING_PROFILES_ACTIVE` | `production` |
   | `DEMO_DATA_ENABLED` | `false` |
   | `SESSION_COOKIE_SECURE` | `true` |

   Keep existing AI settings unless a separate provider-key change is required. Never use the
   owner password, migration password or Supabase API key as the runtime password.
2. Merge the reviewed setup changes through normal CI. The `master` push stages
   a new candidate against the fresh database before promotion. Its first
   authenticated smoke cannot pass until the Production smoke account exists.
   Preserve the existing nonempty `production-candidate` smoke secrets until
   step 4: clearing them makes preflight fail before it creates a candidate URL.
3. Keep protected promotion unapproved. Open the **staged candidate URL**, sign
   in as the new Manager, and create an active synthetic Requester through normal
   account management. Avoid real maintenance data for the smoke account.
4. Update **GitHub environment `production-candidate`** `SMOKE_USERNAME` and
   `SMOKE_PASSWORD` to this Requester's credentials. Leave `preview` secrets tied
   to development accounts; retain the project bypass secret where required.
5. Re-run the failed staging job. Require successful health, login and
   authenticated safe-read smoke. Verify candidate database `facilityflow_prod`
   differs from Preview/local `postgres` while the project reference stays the
   same. Verify development role cannot connect to Production, and Production
   runtime cannot read the development `facilityflow` schema. Verify environment
   account/request data stays separate; never print hashes or passwords.
6. Approve protected promotion of that tested candidate; verify the public domain,
   login and AI readiness. Existing development accounts/sessions will not carry
   over to fresh Production. Record sanitized evidence under `logs/`.

Old Vercel deployments retain old connection settings. Configuration edits alone
do not isolate the currently live deployment. Do not roll back across the cutover
to an artifact that uses development. Backup/restore and alert exercises remain
required. No hosted isolation is claimed until the above checks pass.

Verify backup/export and restore procedures explicitly include `facilityflow_prod`;
do not assume a backup of the default database includes this custom database.

References: [Supabase connections](https://supabase.com/docs/guides/database/connecting-to-postgres),
[multiple databases](https://supabase.com/docs/guides/troubleshooting/manually-created-databases-are-not-visible-in-the-supabase-dashboard-4415aa),
[roles](https://supabase.com/docs/guides/database/postgres/roles),
[SSL enforcement](https://supabase.com/docs/guides/platform/ssl-enforcement).
