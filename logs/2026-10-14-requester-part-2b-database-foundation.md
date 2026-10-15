# Requester Part 2b: database foundation

- Date/timezone: 14 October 2026, Asia/Singapore.
- Role/tools: Codex implementer; Ponytail, Supabase, Postgres best practices,
  Context7 and FacilityFlow handoff. No subagents invoked.
- Task/intention: User resumed implementation after reporting a successful live
  Supabase JDBC check. Deliver the next small database part, with done/left/next.
- Baseline/IDs: Approved TECHNICAL-SPEC v0.1; REQ-SH-001–007/014 prerequisites,
  NFR-SEC-001–002. No complete role requirement claimed verified.
- Sources: Product fields/technical data model, existing code, relevant Postgres
  skill references, official Spring/Supabase/Postgres documentation. Supabase
  changelog checked; linked 15.19/17.11 release changes concern extensions not used
  here. Local test version is recorded separately from hosted platform evidence.
- Changes: Flyway dependencies/profile; V1 private account/audit/session schema,
  RLS and explicit runtime grants; password-free role provisioning; seven genuine
  PostgreSQL migration/constraint/permission/rollback/session/profile tests;
  application test isolation update; setup guides, ADR-005, traceability/reuse/
  plan/status updates and upstream license copy. No MP2 source copied.
- Executed verification: `.\gradlew.bat --offline --no-daemon test bootJar`
  succeeded after initial migration tests, stronger SQLSTATE/profile tests and
  final immutable-field grants. Final run BUILD SUCCESSFUL, 6 tasks. XML reports
  11 tests total: 7 migration, 1 configuration, 3 checker; zero failures/errors/
  skips. Embedded Postgres 2.2.2 with PostgreSQL 17.7.0 is test-only, disposable
  and loopback-bound; no Supabase/developer/production database touched.
- Evidence covered: migrations repeat safely, reject newer version history,
  constrain case-insensitive username/Unicode/role data, deny runtime audit
  modifications/Account deletion/immutable-field changes/schema DDL/history reads,
  roll back Account plus failed audit, cascade session deletion, deny unrelated
  roles through schema and RLS, and boot actual migration profile.
- Corrections: Documentation patch context mismatches corrected before final
  write. Proactively tightened immutable Account update grants and made negative
  tests assert exact SQLSTATEs. No application/security test failures occurred.
- Limitations: Hosted migration/grants not applied or verified; user must privately
  provision credentials and execute documented command. No Request schema,
  repositories, seeding, authentication or session runtime. Runtime schema-version
  validation is explicitly deferred until before first business endpoints.
- Security: No credentials read or recorded. Roles begin NOLOGIN; user sets distinct
  passwords privately. Flyway clean/baselining disabled. Data API remains disabled;
  migration/runtime credentials separate. Preserve unrelated skills/IDE/generated
  files; no staging/commits, merges, cloud writes or teammate messages.
- Human reviewer/outcome: Pending teammate merge review. User-reported prior live
  connectivity is clearly distinguished from tool-executed local evidence.
- Next action: Apply hosted development role setup and V1, report safe success/
  failure; then next small database slice and authentication integration.
