# ADR-007: reuse the existing database for the coursework deployment

The shared-database exception below is superseded by the subsequent explicit
stakeholder decision in [ADR-008](ADR-008-separate-production-database.md).
Retained as historical context; do not apply it to new database configuration.

- Date: 20 October 2026 (Asia/Singapore).
- Status: stakeholder-approved configuration/specification change; teammate
  implementation review remains pending.
- Related requirements: NFR-DEP-001, AI-SEC-019, NFR-DOC-001.
- Approval evidence: user explicitly instructed, "just make use of the one
  database for production" and requested minimal changes and reuse.

Use the existing `facilityflow-dev` Supabase database for the current Vercel
Production deployment. Preserve its schema, application roles, Accounts and
records. Preview/local and Production may share this database and existing
server-side credentials; Vercel environment targets remain explicit. No database
copy, new project, reseed, initialization or schema migration is required by this
decision. Reuse the existing synthetic Requester as the smoke Account and store
its credentials in the GitHub `production-candidate` environment.

Acceptance: Production references the existing runtime database identity and CA;
uses `SPRING_PROFILES_ACTIVE=production`, `DEMO_DATA_ENABLED=false` and secure
cookies; does not run migration/initialization profiles; automated tests remain
isolated. Preview/local actions affect the same records as Production, which must
be stated in the runbook. Secrets stay server-side and out of source/logs. Known
exposed credentials still require rotation; this decision does not waive that.

This is a scoped exception to the earlier separate-database/key requirement,
chosen to minimize coursework deployment changes. It removes environment data
isolation and creates shared rate limits, sessions and write effects. It does not
change authentication, role authorization, validation, AI advisory boundaries,
human merge/promotion gates, backups or recovery requirements. Use separate
databases/credentials before handling real facilities data or independent testing.

Rejected for this scope: provisioning a second Supabase project or moving current
Preview data. No data deletion or security-control bypass is authorized.
