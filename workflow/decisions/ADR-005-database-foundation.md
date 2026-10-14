# ADR-005: Private database foundation and migration identities

- Date: 14 October 2026, Asia/Singapore.
- Status: Implemented against approved TECHNICAL-SPEC; teammate review pending.
- IDs: REQ-SH-001–007/014 prerequisites; NFR-SEC-001–002; TECHNICAL-SPEC.
- Decision: Place account/audit/session tables in private `facilityflow` schema;
  use `facilityflow_migrator` for Flyway, `facilityflow_app` for explicitly granted
  runtime operations. Provision roles separately, initially NOLOGIN/passwordless;
  administrator sets distinct passwords privately. Migration role cannot manage
  roles/databases, runtime cannot create schema objects or alter audit history.
- Rationale: Keep privileged DDL credentials out of runtime and avoid browser Data
  API exposure. RLS role policies reinforce schema isolation without inventing a
  Supabase Auth mapping for Spring Account identities.
- Testing: Pin test-only Embedded Postgres 2.2.2 and PostgreSQL binaries 17.7.0,
  both cached, to run genuine disposable PostgreSQL tests on this Docker-free
  workstation. Version/platform-specific hosted verification remains separate.
- Alternatives: Public-schema browser-accessible storage and owner runtime were
  rejected; Docker cannot be used here because it is unavailable. Flyway remains
  the project-required application schema migration mechanism, taking precedence
  over generic Supabase skill CLI migration-generation guidance.
- Consequences: No default grants on future tables; each migration grants needed
  operations explicitly. Tables are storage prerequisites, not completed user
  workflows. Runtime schema-version validation is deferred only until first
  business endpoints; implement before exposing them. Daily backup/hosted grants,
  actual sessions and production deployment remain unverified.
- Provenance: Spring Session 4.1.1 PostgreSQL DDL adapted under Apache-2.0; no MP2
  source copied. See DatabaseMigrations and REUSE.
- Approval: User resumed database implementation; approved spec baseline governs
  behavior. No teammate merge approval claimed; no cloud writes/merge performed.
