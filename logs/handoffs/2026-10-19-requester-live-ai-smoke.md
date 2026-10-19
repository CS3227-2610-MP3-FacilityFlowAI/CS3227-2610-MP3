# Requester live AI finding handoff

- Source agent: Codex live-test session; destination: next transport developer/verifier.
- Task/goal: user requested live AI testing; record actual outcome without claiming
  release readiness or changing model behavior.
- Approved requirement IDs: REQ-RQ-012–017, REQ-AI-001–006,
  AI-SEC-008/010/019/020; current workflow/specs, source d62cdce.
- Authorized scope/permissions: bounded synthetic provider/gateway smoke only;
  no production source fix, shared database operation, commit or push performed.
- Input artifacts/provenance: logs/2026-10-19-requester-live-ai-smoke.md;
  actual SoClaasTransport/ValidatedAiGateway and SmartReportSchema;
  shared SocLlmHttpsProviderAdapter existing reasoning_effort=none setting.
- Assumptions: local key/model are suitable for requested non-production smoke;
  values withheld, no real maintenance/user records sent.
- Unresolved question: transport fix and authenticated live application smoke
  remain outstanding; broader quality/security and other assistants untested.
- Files changed: verification docs/logs only; ignored diagnostic helpers are not
  production implementation or artifacts to commit.
- Commands/tools: Java production gateway runner and bounded HTTPS diagnostics
  with existing packaged dependencies. No new dependency or configuration edit.
- Verified results: two production gateway attempts failed AI_INVALID_OUTPUT;
  exact-input diagnostic hit 800-token length finish without final content;
  control with reasoning_effort=none passed strict schema using 179 output tokens.
  Five actual generation calls; no database connections or business writes.
- Security considerations: preserve strict TLS/origin/no redirect, 800-token and
  30s caps, strict envelope/schema rejection, no raw content or secret logs.
- Suggestions accepted/rejected: test established shared non-reasoning setting;
  reject relaxing output validation or claiming full live feature success.
- Human approval/reviewer: user authorized tests; no teammate release approval
  inferred. A subsequent fix needs focused regression and fresh verifier.
- Next task: report findings; if authorized to fix, align transport payload,
  verify payload test and repeat small live smoke before full app checks.
- Suggested skills: Ponytail, facilityflow-handoff; no Supabase access needed.
- Status: live smoke found reproducible truncation; production fix pending.

## Authorized fix continuation

User requested alignment and retest. SoClaasTransport now sends
reasoning_effort=none, matching the existing approved INTEGRATION-SPEC completion
contract and shared adapter. SoClaasTransportTest asserts both the exact five
payload fields and the none value. Focused transport/gateway suites plus bootJar
are running in build/requester-nonreasoning-check.log. Next: refresh ignored live
runner runtime from the rebuilt JAR, retest actual gateway synthetic cases, record
results. Fresh verifier must check the two-file fix after the earlier live failure.
No credentials, limits, schema/envelope rejection or database behavior changed.

## Completed fix continuation

Focused SoClaasTransportTest/SmartReportGatewayTest and bootJar passed: 19 tests,
zero failures/errors/skips, 31s. Actual rebuilt production gateway live retest
passed ordinary/missing-detail/untrusted-instruction cases, no retries,
4165/2453/2593ms, output tokens 185/162/174 respectively. No database connections,
business writes or raw content logs. Exact metadata and bounds are in session log.
Fresh merge_verifier static review reported no blockers: five-field payload and
none value match spec/shared adapter; TLS/origin/redirect, deadlines and strict
output bounds/validation remain intact. This supersedes earlier pending-fix
status; it does not claim full authenticated live browser or safety-corpus review.
Full suite not repeated for narrow payload fix. No commit/push. Next: user review
and normal commit/PR workflow if requested; authenticated live app checks remain
separate. Preserve MP3 instructions and local credentials outside commits.

## Authenticated endpoint verified

Subsequent user-requested live test used actual Spring MVC/security/JDBC sessions,
production Requester service/transport and restricted-role disposable local
PostgreSQL through V6. Anonymous302, missing-CSRF403 and Manager403 checks passed,
with no AI metadata for denied calls. Three live create-assistant cases returned
HTTP200/SUCCEEDED with no-store, endpoint latency 3700/3350/2448ms, zero retries;
AI metadata recorded exactly three invocations. All maintenance/submission/update/
work-log counts stayed zero, and manual create form still rendered200. App and
database closed; no shared Supabase access, secrets/raw content logs, code/config
edits, commit or push. See session log for exact token metrics and scope limits.
Authenticated endpoint scope is now verified; actual live browser, owned-edit,
other-role and broader quality/safety checks remain separate. Ignored runner/log:
build/AuthenticatedLiveAiSmoke.java and build/authenticated-live-ai-smoke.log.
