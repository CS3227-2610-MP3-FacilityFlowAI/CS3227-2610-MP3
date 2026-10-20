# Route Smart Report UI through the shared SoCLaaS runtime

- User identified that Smart Report should use the shared runtime. Tracing
  confirmed the UI used SmartReportController -> ai.SmartReportService ->
  ValidatedAiGateway -> SoClaasTransport, independently of the existing shared
  RequesterSmartReportService/SmartReportAssistant runtime. The earlier live
  evidence validated that separate path, not shared-runtime integration.
- Scope/IDs: REQ-RQ-012–017, REQ-SH-018, REQ-AI-001–006 and
  AI-SEC-001/008/017/021/028–030, current workflow/specs.
- Preserve form endpoint and review UI contract. SmartReportService is now only
  a form adapter; RequesterSmartReportService owns admission, lifecycle/readiness,
  typed assistant dispatch, final freshness and telemetry through shared beans.
- Remove duplicate AiConfiguration/AiGateway/AiTransport/AiOperations,
  JdbcAiOperations/SoClaasTransport/ValidatedAiGateway/SmartReportSchema.
  Retire their standalone gateway/transport tests; preserve shared provider/core
  tests and migrate Requester HTTP/authorization/no-write regressions to the
  shared feature port and real shared coordinator/telemetry.
- Combine rough text/current description without silent truncation; reject
  combined descriptions above the shared 2000-code-point bound. Resolve selected
  form category IDs inside shared protected context loading after admission.
  Pass the browser's edit version into the shared probe to close the gap between
  form validation and shared eligibility capture. No AI business writes added.
- Focused form-adapter, service, repository and PostgreSQL integration checks
  are recorded below; initial focused failures and final full success are retained.
- No secret/local config changes or Supabase operations. On 20 October the
  user authorized committing and pushing relevant changes. Preserve existing
  unrelated commits (branch ahead10 at start).

## 20 October verification and commit preparation

- User authorized committing/pushing relevant changes on feat/requester-workflow.
- Initial focused run could not compile a new assertion because assertNull lacked
  its static import; corrected the import. A sandbox Gradle-cache lock denial was
  resolved by rerunning the same check with approved cache access.
- Focused run executed 48 tests: 46 passed, two authenticated HTTP checks failed
  with 503. The fixture registered fake lifecycle/assistant singletons before
  configuration parsing, which replaced them. Registering the same typed shared
  fakes in a BeanFactoryPostProcessor preserves them after parsing. No production
  provider behavior was relaxed to make the tests pass.
- Fresh read-only verifier runtime_verifier reviewed the architecture and this
  fixture correction. No blocking production finding remained. Its requested
  improvement now spies shared RequesterSmartReportRepository.loadContext for
  denied/invalid/quota-limited attempts; the old legacy repository spy did not
  prove the migrated path. Combined input limit is documented in the actual form
  and guide. The synchronous form adapter occupies a servlet thread while shared
  readiness/provider deadlines bound execution; it has no separate runtime.
- All five Node suites passed: Smart Report, request form, shared submission,
  Technician commands and Technician Work Plan. git diff --check passed.
- Full offline check bootJar requesterVerification is running; build passed.
  Final Java/browser result is recorded below. Real provider/browser quality,
  hosted deployment and teammate PR review are not established by these checks.
- No dependency, migration, local credential or Supabase data changes. Earlier
  live smoke used the retired duplicate runtime; no new live call in this run.

### Final automated result

- Gradle --offline --no-daemon check bootJar requesterVerification:
  BUILD SUCCESSFUL in 10m4s. 725 standard Java tests plus one tagged browser/load
  test passed, zero failures/errors/skips. Full rerun includes the corrected HTTP
  fixture and shared-context denial spy; no disabled assertions.
- Browser: 68 checks across 760x600, 1024x700 and 1440x900, including
  Smart Report separate review, keyboard field acceptance/discard and ordinary
  create/edit/update/cancel/password flows. The assistant is a typed shared-port
  test fake; this is not evidence of live model quality.
- 50 distinct authenticated users, 400 manual-workflow requests, zero errors,
  p95 991.72ms with pool size two. Loopback result excludes login/warmup/AI and
  is not a hosted performance claim.
- All five Node suites and git diff --check passed. Fresh verifier reviewed
  production architecture and corrected test wiring with no remaining blocker.
- Commit/push authorized; MP3 instructions, application-local.yml and ignored
  build artifacts excluded. Human teammate review remains required before merge.
