# Assignment integrity and evidence chronology review fixes

- Date: 16 October 2026, Asia/Singapore.
- Request: inspect the two PR reviewer observations supplied in a screenshot.
- Requirements: REQ-MGR-004; NFR-DOC-001; preserve deterministic writes/history.
- Confirmed V2 required assignee/priority for assigned states but omitted the
  assignment timestamp. New V4 pairs assignee and timestamp in both directions.
  Applied V1-V3 are unchanged; inconsistent existing rows cause migration failure
  instead of a fabricated timestamp or deleted history.
- Regression covers null timestamp rejection in ASSIGNED, IN_PROGRESS, COMPLETED,
  CLOSED and CANCELLED; valid assignments succeed; a timestamp without Technician
  is rejected; an unassigned cancelled request remains valid.
- Plan chronology now separates the original 14 October plan from 15 October
  reports and 16 October corrections. Git author/committer timestamps for the
  foundation/completion commits are 15 October 00:17/00:57 +08:00 (14 October UTC).
  No dates rewritten to claim hosted evidence existed earlier. Hosted evidence
  remains user-reported, not independently queried; V4 hosted execution pending.
- Guides, traceability and reuse ledger updated. No MP2 source copied.
- Validation: isolated DatabaseMigrationTest (17) and DatabaseInitializerTest (7)
  passed: 24 tests, zero failures/errors/skips. `gradlew --offline --no-daemon test
  --tests sg.edu.nus.facilityflow.database.DatabaseMigrationTest
  --tests sg.edu.nus.facilityflow.identity.DatabaseInitializerTest bootJar`
  completed successfully in 2m33s, including JaCoCo and JAR. V1-V3 content unchanged;
  `git diff --check` passed. No hosted writes, commit or push performed.
- Files: V4 migration, DatabaseMigrationTest, DatabaseCompletion, REQUESTER-PLAN,
  TRACEABILITY, REUSE and this log/handoff.

## Additional PR suggestions

- Assignment consistency: covered by proposed V4, but fresh verifier found a
  conflict with REQ-TECH-003, which allows unknown migrated timestamps until
  reassignment. Policy clarification requested; V4 is not ready for application.
- Completion-by-state restriction was not applied: REQ-MGR-007/008 and the
  technical spec preserve completion history on return/reopen. Existing regression
  now also checks completed actor/time/summary remain on the reopened request.
- Initialization now sets transaction-local lock_timeout to five seconds before
  advisory lock acquisition. Contention must roll back without partial writes;
  successful retry after release is tested.
- Demo initialization requires explicit development profile and rejects prod or
  production even in mixed profiles before database writes. Development guide
  invocation updated; default Manager bootstrap remains unaffected.
- Fresh security verification: database_verifier checked guards, lock scope and
  rollback/retry coverage. See initializer-fresh-verification handoff. No source
  edits or concurrent test execution delegated; targeted results pending below.

## User-confirmed legacy policy correction

The user chose to preserve the approved REQ-TECH-003 exception. The initial V4
pair constraint above was superseded before any commit/hosted application:
V4 now uses a one-way orphan-timestamp constraint and an invoker trigger enforcing
timestamps only for new assignments and subsequent assignment changes. Existing
V3 null-time assignments can progress with the same Technician until reassignment.
New inserts cannot impersonate legacy records, nor can known timestamps be cleared.
Regression now seeds a real unknown-time V3 row before upgrading to V4 and checks
these paths. Fresh verifier rechecked the revised trigger and tests without
material findings. Guides/traceability now reflect the resolved policy.
Final combined validation passed in 2m31s: DatabaseMigrationTest (18) and
DatabaseInitializerTest (9), 27 tests, zero failures/errors/skips; bootJar and
JaCoCo report successful. Command: `gradlew --offline --no-daemon test --tests
sg.edu.nus.facilityflow.database.DatabaseMigrationTest --tests
sg.edu.nus.facilityflow.identity.DatabaseInitializerTest bootJar`.
V1-V3 content unchanged, whitespace check passed. No hosted writes/commit/push.
PostgreSQL lock behavior reference: https://www.postgresql.org/docs/current/runtime-config-client.html#GUC-LOCK-TIMEOUT
