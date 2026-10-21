# Separate Production database preparation handoff

- Source agent: Codex root.
- Destination agent: deployment_verifier, then operator/next session.
- Task: review minimal documentation/specification change for an isolated fresh
  Production database; no hosted operations delegated.
- Approved IDs: NFR-DEP-001, NFR-SEC-002, NFR-DOC-001. Latest user explicitly
  requires separate databases and selected fresh Production with required accounts
  only; ADR-008 supersedes ADR-007's database exception.
- Scope: read-only review of current diff and new docs/ProductionDatabaseSetup.md,
  workflow/decisions/ADR-008-separate-production-database.md and session log.
  Do not read application-local.yml, secrets or credentials. No DB/cloud mutations,
  deploy, commit, push, merge or promotion delegated.
- Inputs: application initializer, application.yml, bootstrap-roles.sql, migrations
  V1-V6, CI/CD and rollback workflows; existing database guides. Suggested skills:
  Supabase, Postgres best practices, Ponytail, FacilityFlow handoff.
- Assumptions: keep facilityflow-dev for local/Preview and create facilityflow-prod;
  current Production remains shared until a tested isolated candidate is promoted.
- Files: affected specs/guides, ADR-007 supersession note, ADR-008, traceability,
  reuse ledger and sanitized log. Existing release evidence predates this task;
  preserve it. Runtime, migrations, SQL and workflows unchanged.
- Verification: parent inspected source/configuration and checked diff whitespace;
  no new hosted provisioning, migration, bootstrap, smoke or isolation claimed.
- Risks/questions: validate operator profile/config isolation, new smoke account
  sequence before promotion, and old-artifact rollback warning. DB credentials
  must be independent; this request does not require unrelated AI key changes.
- Review requested: identify blocking contradictions or unsafe/incomplete steps.
- Human gate: teammate review before merge and protected approval before promotion.
  No additional user approval needed for already requested preparation.
- Next authorized action: return review findings; parent owns edits/evidence. User
  creates project in dashboard because no connected Supabase management tool exists.
- Status: ready for review; hosted isolation pending.

Fresh verifier outcome: no blockers after independent read-only source/diff
review. Parent added the suggested nonempty smoke-preflight sequencing note;
no authorization, SQL or runtime behavior changed. Relative file links and
whitespace checks passed. Human merge review and hosted provisioning/isolation
remain pending. Next operator action: create facilityflow-prod in the dashboard;
follow docs/ProductionDatabaseSetup.md, without touching development records.

## Superseding single-project review request

User now explicitly chose one Supabase project with separate databases. Prior
separate-project instructions above are historical. Current plan: existing
postgres/dev and fresh facilityflow_prod/Production, with distinct Production
roles and no membership in development roles. New SQL scripts:
workflow/database/bootstrap-production-database.sql and
workflow/database/production-runtime-permissions.sql. V1-V6 remain unchanged.
New ProductionDatabaseIsolationTest passed locally on disposable PostgreSQL17.7.
Review SQL and test plus updated docs/ADR against NFR-DEP-001 and NFR-SEC-001;
read-only, no secrets/cloud/database changes delegated. Look for privilege leakage,
unsafe targets, incorrect RLS or future migration handling and setup gaps. No
hosted isolation claimed; parent owns edits. Suggested same skills as above.

Review found stale Production grants could survive later donor-role revocations.
Parent fixed permission synchronization to revoke column, table/view and sequence
grants inside the atomic DO before copying current migration ACLs. Added table,
column and sequence revocation regression; targeted test passed again. Fresh
verifier requested to validate this fix. User has no PostgreSQL client and wants
installation guidance; next operator tool is DBeaver Community, not a new project.

Fresh verifier validated the fix and inspected the passing targeted test report:
one test, no failures/errors/skips; no further scoped blockers. No hosted SQL or
settings changed. Current next user step: install/open DBeaver Community, then
connect using existing project owner credentials privately with verified TLS.
Teammate merge review and protected Production cutover approval remain required.
