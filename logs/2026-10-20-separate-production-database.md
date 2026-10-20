# Separate Production database preparation

- Authority: user superseded the single-database decision and explicitly selected
  a fresh Production database with only required accounts. NFR-DEP-001,
  NFR-SEC-002, NFR-DOC-001; ADR-008 records the new decision.
- Branch: chore/separate-production-database, created from origin/master with
  pre-existing uncommitted release evidence preserved.
- Prepared: separate-project procedure, existing migrator/runtime roles and V1-V6
  migrations, no-demo Manager bootstrap, normal Manager-created smoke Requester,
  environment-specific Vercel connections and GitHub smoke credentials, staged
  cutover and the old-artifact rollback limitation. No application/SQL changes.
- Read: current initializer, application configuration, role provisioning,
  deployment workflow and affected guides/specifications. Supabase changelog
  index and connection documentation checked; reviewed the PostgreSQL minor
  release advisory. No hosted SQL queries or project changes performed.
- Hosted state: current Production still uses facilityflow-dev. No connected
  Supabase management tool is available in this session. User must create the new
  project; no new credentials requested in chat, read or recorded.
- Pending: project creation, new-project role setup, migrations, bootstrap,
  authenticated smoke, human-reviewed merge/promotion and independent isolation
  verification. Production separation is not yet complete. Backup/restore and
  monitoring evidence remain separate requirements.
- Fresh deployment_verifier independently reviewed docs/specs against source and
  found no blockers for the current cutover. Clarified that existing nonempty
  staging smoke secrets must remain until creating the new smoke account, to
  avoid preflight preventing candidate creation. Relative file links and
  whitespace checks passed. No runtime tests rerun for documentation-only changes.

## Subsequent single-project implementation

- User selected two databases in the existing Supabase project, superseding the
  earlier separate-project plan. ADR-008 and active guides/specs now distinguish
  existing postgres/development from new facilityflow_prod/Production; shared
  owner administration, resources and project-level failures remain explicit.
- Added production-only bootstrap and post-migration permissions SQL, plus
  ProductionDatabaseIsolationTest. Distinct roles avoid inheritance of development
  privileges. Existing V1-V6 checksums and application code remain unchanged.
- Executed targeted Gradle test against disposable local PostgreSQL 17.7: passed.
  Verified no copied development records, successful Production runtime reads/
  writes, denied cross-environment access, immutable audit/account-delete/schema/
  migration-history restrictions, wrong-target refusal and safe script reapplication.
- Initial sandbox invocation could not use C:/.gradle; authorized local test rerun
  succeeded. No hosted connections, new credentials or configuration edits made.
- Next: independently review SQL/tests and updated procedure, then run owner
  bootstrap via trusted PostgreSQL client, validate actual hosted pooler access,
  migrate/bootstrap, stage/smoke and human-promote. No new Supabase project needed.
- Fresh review identified stale privileges after later migration revocations.
  Fixed by atomically resetting Production column/table/view/sequence ACLs before
  copying donor permissions. Added column/table/sequence revocation regression;
  targeted Gradle test passed again. Hosted settings remain unchanged. User needs
  PostgreSQL client installation guidance; selected DBeaver Community for next step.
- Fresh verifier subsequently confirmed the blocker resolved and independently
  inspected the passing targeted report (one test, zero failures/errors/skips).
  No further scoped findings; no hosted verification claimed. DBeaver Windows
  installer guidance checked against official download/installation documentation.
- Subsequent user screenshots show verified-TLS DBeaver connectivity and a query
  result identifying current_database=facilityflow_prod and
  current_user=facilityflow_prod_migrator. This is user-provided hosted evidence,
  not an independent session query. Production schema migration and live cutover
  remain pending; no credential values retained in this record.
- 21 October follow-up: user supplied migration output showing successful
  application of all six Flyway migrations to facilityflow, now version6, and
  clean application/pool shutdown. Earlier retries failed before connecting
  (username typo, then password authentication); no repair/clean was used.
  Evidence is user-provided terminal output, not an independent hosted query.
  Production runtime-permission mapping, Manager bootstrap and live cutover
  remain pending. No credentials retained from attachments.
- 21 October: user reported Production runtime permission script succeeded and
  provided app-role database-check output with Database connection verified.
  Subsequent database-initialize,production output reports Database initialization
  complete and clean shutdown. Based on the inspected no-demo initializer, this
  is consistent with one Manager plus its audit event; independent hosted counts
  have not been queried by this session. Vercel Production configuration/cutover
  and Production smoke Requester are still pending. No secrets recorded.
