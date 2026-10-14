# ADR-004: Supabase-hosted PostgreSQL

- Date: 14 October 2026, Asia/Singapore.
- Status: Provider choice explicitly authorized by user; implementation review
  pending. Live project/connection not verified.
- Context/IDs: TECHNICAL-SPEC PostgreSQL/configuration; NFR-SEC-001–002,
  NFR-DEP-001, NFR-REL-002. User will create the database and asked to pause
  further work while preparing Supabase integration/setup guidance.
- Decision: Use Supabase PostgreSQL via Spring JDBC and the Boot-managed pgJDBC
  driver. Local development uses session pooling, certificate-verified TLS,
  environment-backed credentials and a small two-connection Hikari pool.
  Add an explicit read-only connection smoke profile; no automatic SQL init.
- Options: Local PostgreSQL setup is deferred by user direction. Supabase Auth
  and browser Data API access would change the approved app boundary and are
  not required for this provider choice.
- Consequences: Prepare separate development/production projects. Restrict
  runtime credentials before writes; migrate with Flyway in a later part.
  Serverless pooling/production sizing, backups and hosting remain unverified.
- Security: Download CA and preserve verification; credentials are server-only.
  Disable unused Data API. Tests remain isolated from hosted developer data.
- Sources: Official Supabase connection/security/backup and pgJDBC SSL docs,
  linked in docs/SupabaseSetup.md; Context7 JDBC guidance.
- Approval: Explicit user provider decision; teammate non-trivial merge review
  pending. No merge, cloud provisioning or migration performed.
- Supersedes: None; implements the provider configuration choice.
