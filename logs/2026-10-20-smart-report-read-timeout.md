# Restore Smart Report pre-admission transaction timeout

- PR screenshot correctly identified that 5fc0059 removed the prior explicit
  five-second TransactionTemplate timeout. Verified against its parent source.
- REQ-RQ-012-017, REQ-AI-004 and AI-SEC-008/021: restore setTimeout(5) on the
  existing form adapter transaction. Both create/edit requester reads use that
  transaction; the shared AI runtime is still invoked after it ends.
- SmartReportFormAdapterTest injects QueryTimeoutException, captures the five-
  second transaction definition, verifies rollback and no shared AI invocation.
  Controller already handles DataAccessException as no-store HTTP 503 with safe
  guidance; no new error mapping or dependency needed.
- Focused adapter/shared-service tests: 13 tests passed, no failures/errors/skips.
  Command: gradlew --offline --no-daemon test --tests
  sg.edu.nus.facilityflow.ai.SmartReportFormAdapterTest --tests
  sg.edu.nus.facilityflow.requester.RequesterSmartReportServiceTest.
  BUILD SUCCESSFUL in 30s; git diff --check passed.
- Fresh read-only runtime_verifier checked restored scope, regression and existing
  controller handling; no blocker. It did not execute tests. Full browser/load,
  live provider and hosted database contention tests were not repeated.
- This restores the transaction/JDBC query bound, not a universal five-second
  HTTP/network/connection-acquisition guarantee. Pool acquisition retains its
  existing separate timeout. No schema, private configuration or cloud data change.
- Earlier shared-runtime live retest succeeded as reported by the user; no
  independent live retest in this fix. No credentials or raw content recorded.
- User authorized committing and pushing this verified fix on 20 October.
  MP3 instructions remain excluded. Teammate review required before merge.
