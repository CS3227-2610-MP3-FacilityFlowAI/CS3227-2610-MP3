# Complete shared database storage and explicit initialization

- Date/timezone: 15 October 2026, Asia/Singapore.
- Task: User asked to complete database preparation on feat/database-migration,
  continuing small parts. No new commit/push/merge requested.
- Role/tools: Codex implementer; Supabase/Postgres best practices, Ponytail,
  Context7 for Flyway/password storage, PowerShell/Gradle and FacilityFlow handoff.
  Fresh database_verifier invoked only because AGENTS rule 5 required independent
  verification following a failed exact-error assertion.
- Sources/IDs: Approved v0.1 PRODUCT/TECHNICAL/role/integration/AI-security specs;
  REQ-SH-007/009/014–017; REQ-RQ-002/008; REQ-TECH-006/007; REQ-MGR-016–019/023;
  REQ-AI-007–010; AI-SEC-008/017/021. Existing V1 and earlier hosted screenshots.
- Changes: V2 maintenance/category/update/work/completion storage, generated
  bounded Request display identity; V3 skills, append-only typed AI metadata and
  rolling timestamps/global-slot leases; explicit grants/RLS/indexes. All 14
  technical-model tables are supplied including history. No applied V1 edits.
- Initialization: Opt-in database-initialize profile, default demo off, salted
  PBKDF2 hashes, bootstrap Manager or explicit six-account/all-state demo data,
  atomic audits/business records, advisory initialization lock, no reseeding/
  overwrite and refusal over existing business/catalogue changes.
- Password decision: Spring Security crypto 7.1.1 under Boot BOM; v5.8 PBKDF2
  defaults (16-byte salt, 310,000 SHA-256 iterations, 256-bit output) and delegated
  hash prefix; Unicode 24-code-point password verified without bcrypt truncation.
- Initial test: test bootJar executed 19 tests; 18 passed/1 assertion failed.
  Explicit generated-column UPDATE emitted SQLSTATE 428C9 before privileges,
  rather than expected 42501. Corrected exact expected code, preserving forbidden
  mutation rejection; no database permission weakened.
- Fresh verification: database_verifier independently reviewed V2/V3/specs and
  executed .\gradlew.bat --offline --no-daemon test bootJar successfully in 54s,
  19 tests, zero failures/errors/skips; no material findings or code/cloud edits.
- Subsequent verification: Full 24-test build passed with initialization tests;
  actual initialization-profile test then passed separately. Added existing
  catalogue protection and final full .\gradlew.bat --offline --no-daemon test
  bootJar completed BUILD SUCCESSFUL in 2m17s. Final XML: 26 tests total, zero
  failures/errors/skips (15 migration, 7 initialization, 1 configuration, 3 probe).
- Tested: V1→V3 upgrade/reruns/newer rejection, Unicode/foreign-key/enums/shapes,
  identity overflow, append-only grants/immutable fields, history retention,
  metadata/null usage, rolling-limit CAS storage, RLS/private ownership, hashed
  demo/bootstrap/rollback/idempotence/concurrency/profile and catalogue preservation.
- Docs/artifacts: DatabaseCompletion guide, current guide/status/traceability/
  reuse updates, ADR-006, verification/final handoffs and this summary.
- Corrections/unresolved: Hosted V2/V3 and initialization await user application.
  Runtime schema validation, business authorization/eligible assignees/named
  transitions/history visibility and actual gateway quotas remain service work;
  no finished role workflow claimed. Production backup/release remains pending.
- Security: Disposable loopback PostgreSQL only. No hosted database credentials
  read/writes, no real accounts copied, no raw model content storage. Supabase
  changelog checked; migration flow remains project-required Flyway.
- Reuse: V2/V3/initializer/tests are new code; retained MP2 demo names/password/
  lifecycle scenarios are concept-only from approved specs, recorded in REUSE.
- Permissions/worktree: Preserve user's .gitignore edit, downloaded skills and
  skills-lock. No staging/commit/push; never commit MP3 instructions. V1 unchanged.
- Suggestions accepted/rejected: User requested completion; no product requirement
  changes or teammate approval invented.
- Human reviewer/outcome: Pending teammate review; independent agent verification
  is tool evidence, not human merge approval.
- Next: User applies V2/V3 as migrator, initializes fresh dev data explicitly as
  app role, reports metadata/count outcomes; then login/Requester integration.
