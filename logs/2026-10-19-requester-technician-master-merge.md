# Integrate Technician master changes into Requester branch

- Date: 19 October 2026, Asia/Singapore.
- User request: merge latest master into the current branch and resolve conflicts.
- Parents: Requester bd5dbfc; fetched origin/master 7c7be69.
- Requirements: REQ-RQ-001–017, REQ-TECH-001–017, REQ-SH-002/004/006/010/012/014;
  preserve existing Manager behavior and approved workflow/specs.
- Conflicts: shared password service/tests, dashboard controller/template, About,
  security test reset, Developer/User guides, reuse and traceability.
- Resolution: preserve RequesterRequestController's /requester route and incoming
  Technician queue delegation; use SharedPagesController for password/About and
  incoming PasswordChangeService/JdbcPasswordChangeRepository for one password
  route with exact confirmation, persisted authorization, atomic audit and retained
  sessions. Remove obsolete PasswordController/password template; keep internal
  JdbcIdentityOperations regression coverage without routing web writes to it.
- Requester pages use the canonical authenticated header and main-content target.
  Browser password scenario supplies confirmation and checks the shared success
  message. Two incoming service fixtures use explicit CurrentAccount sessionVersion.
- Preserve Requester password mutation assertions by moving their all-role,
  Unicode, next-login/session, validation, CSRF and no-echo scenarios from the
  H2 password-service-double context into real PostgreSQL integration tests.
  PasswordChangeFixtures now migrates through V6 for web readiness.
- Documentation retains all role implementation/reuse evidence and V6 history;
  obsolete placeholder/password descriptions are reconciled.
- Checks: five actual-script Node suites passed. Gradle
  `--offline --no-daemon check bootJar requesterVerification` passed in 11m24s.
  Standard Java suite: 734 tests, zero failures/errors/skips; tagged browser/load
  integration: one test, zero failures/errors/skips. JaCoCo/check/JAR succeeded.
  Chrome 155.0.8059.82 passed 68 combined checks across three viewports, including
  Requester creation/edit/update/cancellation, owner isolation and the consolidated
  password confirmation/session flow. Local load: 50 users, 400 requests, zero
  errors, pool 2, p95 567.09ms. This is loopback evidence, not hosted performance
  or full manual accessibility certification. Ignored artifacts are under
  build/test-results, build/verification/requester and
  build/master-technician-merge-check.log.
- Scope limits: no shared Supabase queries/writes, migration history changes,
  live LLM calls, credential edits or push. MP3 instructions remain excluded.
- Human teammate review and hosted release acceptance remain separate gates.

## Fresh verifier

The independent merge_verifier performed a read-only static check of the staged
repairs and reported no blockers. It verified single password/About and role route
ownership, Requester canonical header/main targets, persisted session/role checks
before password reads, atomic safe audit and retained sessions, and real PostgreSQL
password regression coverage through V6. It did not run tests or inspect secrets;
the root session remains responsible for actual Gradle/browser results.

Canonical shared password service/repository/controller match incoming master
exactly; the verifier also confirmed staged whitespace checks. Root final checks
above passed. All conflicts are resolved; no source migration, hosted database
operation or push is included in this local merge.
