# Fresh initializer/security review handoff

- Source agent: fresh database verifier.
- Destination agent: parent implementation session / teammate reviewer.
- Task/goal: independently review demo-profile guard, bounded initialization
  lock waits, assignment constraint and completion retention after review findings.
- Approved requirement IDs: REQ-SH-014–017, REQ-MGR-004/007/008,
  REQ-TECH-003; approved specification v0.1 and TECHNICAL-SPEC.
- Authorized scope/permissions: read-only implementation review and this handoff;
  no source edits, tests competing with parent run, hosted access, commits or push.
- Relevant input artifacts/provenance: DatabaseInitializer.java,
  DatabaseInitializerTest.java, DatabaseMigrationTest.java,
  V4__require_assignment_timestamp.sql, docs/DatabaseCompletion.md;
  workflow/specs PRODUCT-SPEC, ROLE-MANAGER, ROLE-TECHNICIAN, TECHNICAL-SPEC.
- Assumptions: Spring profiles are operator-controlled; separate production
  credentials remain necessary. Application services enforce human authorization.
- Unresolved questions: none for assignment-time policy. The user explicitly chose
  to preserve REQ-TECH-003's approved legacy exception; revised pending V4 implements
  it without changing approved specifications or inventing historical timestamps.
- Files changed: this verifier handoff only; temporary handoff outside repository.
- Commands/tools: Get-Content, rg, git diff/status; no tests executed by verifier
  in this review. Parent owns the current targeted database/initializer test run.
- Verification performed/results/evidence: source review finds explicit
  development allowlist plus prod/production denylist before database writes;
  tests cover absent/prod/production/mixed profiles. SET LOCAL lock_timeout is
  inside TransactionTemplate before the advisory transaction lock; test checks
  SQLSTATE 55P03, zero account/audit rows and successful retry after lock release.
  Completion fields/history remain after CLOSED-to-ASSIGNED, matching requirements.
  Fresh follow-up source review confirms revised V4 uses an invoker trigger plus
  an orphan-timestamp CHECK: existing V3 unknown times survive same-assignee
  progress/metadata updates; inserts, changed assignees, return/reopen to ASSIGNED
  and clearing a known timestamp require an assignment time. Regression setup
  creates an unknown-time assignment before V4 and checks preservation and rejection
  paths after upgrading. No material code findings in this revised implementation.
- Security considerations/unresolved risks: no hosted verification; profiles alone
  cannot distinguish a production database if operators provide development settings.
- Suggestions accepted/rejected: accept bounded locking and profile guard; reject
  state-based clearing/prohibition of retained completion metadata because history
  must survive return/reopen. Initial blanket timestamp constraint was revised
  following the user's explicit decision to retain the approved legacy exception.
- Human approval required and reason: teammate approval before non-trivial merge.
- Human reviewer/outcome/evidence: pending.
- Next authorized action: parent collects targeted test outcome, updates upgrade
  documentation to the resolved policy, and reports remaining hosted V4 verification.
- Suggested skills: facilityflow-handoff, supabase-postgres-best-practices, Ponytail.
- Status: revised source review complete; policy resolved; parent test results pending.
