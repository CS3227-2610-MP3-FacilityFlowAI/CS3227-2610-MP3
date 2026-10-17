# Manager Triage orchestration increment

- Increment: `feat(manager-ai): orchestrate triage suggestions`.
- Requirements: REQ-MGR-020–025 and AI-SEC-001–008/028–030.
- Change: added Manager-only POST triage orchestration for eligible non-terminal
  Requests, server-side Requester Update selection (latest ten, sent oldest to
  newest), active category/skill projections, active Technician/workload
  snapshots, shared admission and durable TRIAGE telemetry, one absolute
  deadline, captured Manager-session authorization, deterministic candidate
  ranking, and post-call Request/catalogue/candidate freshness revalidation.
  The endpoint is advisory and performs no assignment or Request mutation.
- Regression coverage: service tests cover typed success/ranking, wrong role,
  invalid/ineligible request, admission rejection and stale async discard;
  isolated H2 JDBC tests cover bounded context, sorted catalogues, workload
  facts and Request/session/catalogue freshness; web integration verifies POST,
  CSRF, Manager role enforcement and `no-store` behavior.
- Focused verification: `./gradlew.bat test --tests
  '*ManagerTriageServiceTest' --tests '*JdbcManagerTriageRepositoryTest'
  --tests '*SecurityIntegrationTest' --tests '*SocLlmTriageAssistantTest'
  --no-daemon --console=plain` — BUILD SUCCESSFUL, 24 tests, zero
  failures/errors/skips.
- Review/approval: locally verified; role-owner, teammate and Security Reviewer
  approval remain pending.
- Remaining scope: ordinary Manager confirmation, editing and assignment
  operations remain separate deterministic workflow work. Browser/UI review
  acceptance is not claimed by this increment.
