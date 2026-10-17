# Requester Smart Report orchestration increment

- Increment: `feat(requester-ai): orchestrate smart report requests`.
- Requirements: REQ-RQ-012–017 and AI-SEC-001–008/028–030.
- Change: added authorization-first create and owned-OPEN-edit Smart Report
  orchestration, active-category context projection, shared admission and durable
  telemetry, one absolute deadline, captured-session and persisted-state
  freshness revalidation, and CSRF-protected POST endpoints with
  `Cache-Control: no-store`. Results remain suggestions and this increment does
  not persist Request mutations.
- Regression coverage: service tests exercise the successful typed result,
  invalid input, wrong role, inaccessible edit, admission rejection and
  in-flight access loss; isolated JDBC tests exercise owner/category context and
  request/session/catalogue freshness; web integration verifies CSRF, role and
  no-store behavior.
- Focused verification: `./gradlew.bat test --tests
  '*RequesterSmartReportServiceTest' --tests
  '*JdbcRequesterSmartReportRepositoryTest' --tests
  '*SecurityIntegrationTest' --no-daemon --console=plain` — BUILD SUCCESSFUL,
  21 tests, zero failures/errors/skips.
- Full verification: `./gradlew.bat test --no-daemon --console=plain` — BUILD
  SUCCESSFUL, 490 tests across 29 suites, zero failures/errors/skips.
- Review/approval: locally verified; role-owner, teammate and Security Reviewer
  approval remain pending.
- Remaining scope: browser review-panel interaction and the ordinary Request
  create/update workflow are separate Requester-module work. Manager Triage is
  the next gateway orchestration increment.
