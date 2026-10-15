# Requester Part 2a: Supabase connection preparation

- Date/timezone: 14 October 2026, Asia/Singapore.
- Role/tools: Codex implementer; Ponytail, Context7 and FacilityFlow handoff.
  No subagents invoked.
- Task/intention: User will provision Supabase and asks to pause further work;
  prepare Supabase integration and actionable setup instructions now.
- Scope/IDs: TECHNICAL-SPEC PostgreSQL/configuration prerequisite;
  supports NFR-SEC-001–002 and NFR-DEP-001 without claiming full compliance.
- Sources: Approved technical baseline, existing Part 1, explicit user provider
  choice, current Context7 Spring/Supabase docs and official provider/pgJDBC docs
  linked in docs/SupabaseSetup.md.
- Changes: JDBC/PostgreSQL dependencies; required environment-backed datasource,
  verify-full TLS/root certificate, bounded connection timeouts and two-connection
  local pool; disabled SQL initialization; read-only database-check profile;
  tests, setup guide, ADR-004, source/provider/status/traceability/reuse records.
- Tests executed: `.\gradlew.bat --offline --no-daemon test bootJar` completed
  BUILD SUCCESSFUL (6 tasks). XML: 4 tests total, zero failures/errors/skips.
  Configuration test uses explicit synthetic overrides and opens no connection;
  3 mock-backed checker tests cover SELECT 1 only, safe provider failure and
  unexpected response. No tests touched developer/production data.
- Limits: Hosted project/certificate/connection is not verified; no migrations,
  business tables/writes, account/session integration or deployment. Normal
  datasource startup is lazy; live readiness requires explicit smoke command.
- Decisions: Supabase hosts PostgreSQL; Spring identity design unchanged. Local
  session pooler; hosted serverless mode/migrations require later validation.
- Suggestions accepted/rejected: Provider choice explicitly accepted by user;
  no other product behavior changes approved.
- Security: No credentials read or committed; setup uses a masked local password
  prompt. Dashboard setup disables unused Data API. Restricted runtime/migration
  credentials and backup/restore remain later work. MP3 instructions untouched;
  no staging, commits, cloud provisioning or messages to teammates.
- Human reviewer/outcome: Pending teammate implementation review. Plan is paused
  after connection preparation per user request, awaiting project setup.
