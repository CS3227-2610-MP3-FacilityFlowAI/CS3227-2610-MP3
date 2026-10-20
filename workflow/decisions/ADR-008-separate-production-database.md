# ADR-008: separate development and Production databases

- Status: stakeholder-approved decision; hosted Production cutover verified
  21 October 2026. Setup branch still requires teammate merge review.
- Supersedes ADR-007's shared-database exception, not its historical evidence.
- Requirements: NFR-DEP-001, NFR-SEC-002, NFR-DOC-001.
- Authority: the user requires separate databases and selected a fresh Production
  database containing only required accounts rather than copying development data.

Retain `facilityflow-dev` for local development and Vercel Preview. Create a
separate database, `facilityflow_prod`, inside the existing Supabase project, as
subsequently requested by the user. The owner/project/server remain shared; use
new `facilityflow_prod_migrator` and `facilityflow_prod_app` roles and passwords. Reuse the existing schema migrations and application;
no application changes, edited migrations or data copy are required. Use the
Production database bootstrap and post-migration permission scripts documented
in the operator guide; never grant membership in development roles. Provision
roles and apply all committed migrations to the empty Production database, then
bootstrap one Manager without demo mode. Create its synthetic Requester smoke
account through the authorized Manager workflow.

Vercel Production alone uses the new runtime connection; Preview and local
configuration retain development connections. GitHub `production-candidate`
receives the new Production smoke credentials. The existing project-wide bypass
secret can remain. This database decision does not require unrelated AI/provider
configuration changes; AI-SEC-019 continues to govern provider keys.

The currently deployed application remains on the shared database until a newly
staged candidate using the isolated database passes health, login and authenticated
read checks and receives protected human promotion. During this transition the
old deployment remains shared; separation is not yet verified. No development
records, sessions, audits or password hashes are copied, deleted or reseeded.

After cutover, application rollback must target a deployment configured with the
new Production database. A pre-cutover artifact still points to development and
must not be promoted as an isolated Production rollback. Schema rollback remains
a separate restore operation. Backup/restore and monitoring requirements remain.

Operator procedure: [ProductionDatabaseSetup](../../docs/ProductionDatabaseSetup.md).

The custom database is managed through PostgreSQL clients and JDBC; it is not
visible in Supabase Table Editor or integrated with its Data API/Auth/Storage.
Project administration, server capacity and project-level failures remain shared.
This provides logical database/credential separation, not separate infrastructure.
Validate non-default database connectivity on the actual hosted pooler before
changing runtime targets. Preserve all existing development grants/passwords.

Verification: [21 October cutover evidence](../../logs/2026-10-21-production-database-cutover.md)
records successful promotion, live checks and corrected Production/Preview
configuration. Historical pending-cutover text above describes the transition
procedure; the current live Production artifact uses the isolated database.
No new Preview deployment smoke or recovery/rollback exercise is claimed.
