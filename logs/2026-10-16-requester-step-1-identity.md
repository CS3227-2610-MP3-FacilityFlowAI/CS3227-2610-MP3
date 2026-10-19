# Requester step 1: shared password and identity contracts

- Date: 16 October 2026 (Asia/Singapore).
- Branch: `feat/requester-workflow`, created at the user's explicit request.
- Authorization: implement the Requester steps in order and report at each checkpoint; no merge, push or deployment requested.
- Requirements: REQ-SH-002/004/006/012/014, NFR-SEC-001/002; approved v0.1 specifications in workflow/specs/.

## Implemented

- Shared GET/POST `/account/password` for the three authenticated roles, with
  current-password verification, 8–24 Unicode code-point validation, existing
  Argon2 encoder, CSRF and no password values in page models.
- Server-derived identity and persisted active/role/session-version checks.
  A conditional database write rejects changed credentials/account state;
  password update, row-version increment and audit append share one transaction.
  Own-password changes preserve the session version and current session.
- Audited successful login. Audit failure clears authentication and invalidates
  the login session instead of returning a successful authenticated workspace.
- Shared authenticated header fragment and workspace/password links. About and
  complete role navigation remain pending.
- Updated guides, traceability, reuse ledger and Requester plan. Manager reset,
  deactivation and role changes are documented integration contracts, not claimed
  as implemented services.

## Provenance and decisions

Read the product/technical/Requester specs, current security implementation and
all affected callers/tests. The password-page interaction is settled by retained
MP2 behavior at `9819b857d880134c1fc1ffef8a8bcd5d6e536f43`,
`AuthenticationService.changePassword` and the `ApplicationRouter` password page,
under workflow/specs/README.md precedence. This retains current/new inputs and
current-session behavior without introducing a confirmation field or a new
password composition rule. No MP2 source code copied.

Applied Ponytail, Context7 and PostgreSQL best-practices skills. Context7 supplied
official Spring Security form-login success processing documentation. No hosted
database or provider secrets were queried. All database tests are disposable.

## Verification and correction

- Initial sandbox Gradle launch stopped before compilation because its default
  cache resolved to unwritable `C:/.gradle`; reran with the existing user cache
  through approved tool escalation.
- First focused run compiled and packaged successfully. PostgreSQL/authorization
  tests passed; H2 web-test setup failed because the new fixture placed `DEFAULT`
  after `PRIMARY KEY`. Corrected H2-only column order; no production migration changed.
- Focused rerun: `gradlew.bat test --tests '*SecurityIntegrationTest' --tests
  '*DatabaseMigrationTest' --tests '*IdentityAuthorizationTest' --tests
  '*AiAccessGuardTest' bootJar` succeeded; 39 tests, zero failures/errors/skips.
- Independent verifier `/root/verify_identity` inspected the fix, implementation
  and fresh XML, finding no blocking correctness/security issue. It identified
  About navigation as remaining scope, already explicitly marked partial.
- Full-suite result: `gradlew.bat test bootJar` succeeded in 3m32s. All 53 tests
  passed with zero failures/errors/skips, including PasswordChangeServiceTest;
  JaCoCo generated. Counts checked from build/test-results/test/TEST-*.xml.
- `git diff --check` passed before final evidence updates.

## Remaining and next

Manager account-administration use cases, About/navigation completion, runtime
schema-readiness verification (existing deferred configuration), CI, human
teammate approval and browser/hosted verification remain pending. The next
Requester slice is create → own list → detail, with request validation,
ownership-scoped reads, audit transactions, concurrency and failure tests. Later
steps add edit/cancel/updates, dashboard/search/filtering and the advisory AI.

No merge, commit, push, deployment or external messages performed. Human teammate
review remains required before a non-trivial merge; agent review is separate.
