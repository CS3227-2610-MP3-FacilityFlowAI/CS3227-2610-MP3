# Requester step 6: AI review and SoCLaaS adapter

Date: 16 October 2026 (Asia/Singapore). Branch: `feat/requester-workflow`.

## Authorization and scope

The user requested committing step 5 and continuing step 6. Step 5 was committed
as `3dc3a60` (`feat(requester): add bounded Smart Report service (REQ-RQ-012/014)`).
Step 6 remains uncommitted at this checkpoint. The user explicitly selected
**Defer the live check; finish local implementation and tests** when asked about
non-production configuration. No external provider call, credential retrieval,
hosted database mutation, push, merge or deployment was performed.

Sources: approved v0.1 `workflow/specs/ROLE-REQUESTER.md`, `INTEGRATION-SPEC.md`,
`AI-SECURITY-SPEC.md`, shared product/technical contracts and section 8 of
`workflow/REQUESTER-PLAN.md`. Scope: REQ-RQ-015–017 and supporting REQ-RQ-012–014,
REQ-SH-018, REQ-AI-001–006 and relevant AI-SEC-005/008–011/013–020/028–030.
Shared gateway ownership remains with `ngkhengyang`; this contribution does not
transfer that accountability or establish teammate review.

Only configuration-variable presence was checked; none of AI_ENABLED,
SOCLAAS_BASE_URL/API_KEY/MODEL was configured in the current process. Secret
values were not printed or read from local secret files. No model was selected
or hard-coded for runtime. Test model/key values are synthetic.

## Implemented

- Optional Smart Report section in the shared create/eligible-edit template,
  outside the business form. Labels explain advisory review and explicit save.
  Rough text plus current report fields are sent to the existing same-origin
  CSRF-protected POST endpoint; edit includes the expected Request version.
- `smart-report.js` displays fields/questions/rationale via textContent. Each
  field needs its own acceptance click. Category labels map to current option
  IDs; urgency maps to the ordinary enum. Accepted values remain editable and
  ordinary Submit/Save remains the only business-write action.
- Discard preserves manual fields and clears transient suggestions. Changed
  input during inference discards late output. A later manual edit prevents an
  old field suggestion from overwriting it. Discard/submission/navigation abort
  or ignore browser results and clear suggestion nodes. No chat/localStorage,
  automatic form overwrite or auto-submit. Manual/no-JavaScript path remains.
- `SoClaasTransport` replaces the disabled placeholder through the existing
  conditional bean. Runtime restricts the configured URL to the approved HTTPS
  SoCLaaS `/v1` origin, default certificate validation and no redirects; Bearer
  key stays server-side. Missing/invalid config leaves AI unavailable.
- First-use GET `/models` runs after role/service admission, never at startup or
  `available()`. One probe per process at a time, at most two seconds within the
  remaining invocation deadline. Success caches five minutes; failure backs off
  60 seconds. Configured model must appear exactly in the response.
- Non-streaming POST `/chat/completions` sends only model, stream=false,
  max_tokens and exactly system/user messages. No tools/history/response ID.
  Model discovery and generation share the existing original bounded deadline.
  Cancellation cancels the HTTP future; response bodies are capped at 64 KiB.
- Envelope validation requires one completed assistant-text choice and rejects
  malformed/truncated/tool/refusal output before the existing strict content
  parser. Typed transport exceptions avoid retrying validation/overflow or
  readiness failures as network faults. HTTP statuses/short Retry-After map to
  the existing retry ceiling. Provider error bodies never reach callers.
- Captures returned numeric non-negative usage; absent/invalid usage stays null.
  Existing role/state/version/freshness checks, database quotas/leases and
  metadata-only persistence are unchanged.

No migration or dependency was added. No other role feature or business
transition was added. No MP2 source consulted/copied. Guides, traceability,
progress, reuse and handoff records describe the final implemented behavior.

## Executed checks

- `gradlew.bat compileJava`: BUILD SUCCESSFUL in 17s.
- Focused `gradlew.bat test --tests '*SoClaasTransportTest' --tests
  '*SmartReportGatewayTest' bootJar`: **18 tests passed**, BUILD SUCCESSFUL in 21s.
- Expanded focused command adds `--tests '*RequesterRequestIntegrationTest'`:
  **50 tests, zero failures/errors/skips**, BUILD SUCCESSFUL in 1m11s (8 local
  HTTP, 10 gateway and 32 real PostgreSQL workflow tests).
- `node src/test/js/smart-report.test.cjs`: passed review/individual acceptance,
  label-to-ID/urgency mapping, no automatic mutation/submit, discard, manual
  field changes, late output, cancellation, submission/history cleanup, failure,
  oversized rough input, CSRF/version payload and text-only rendering checks.
- `node src/test/js/request-form.test.cjs`: existing pending-submit/default
  decision/back-navigation checks passed.
- Initial full `gradlew.bat test bootJar`: BUILD SUCCESSFUL in 3m39s, before the
  final model-secret configuration guard below.
- Full run after model-secret guard `gradlew.bat test bootJar`: **112 tests across 14 suites,
  zero failures/errors/skips**, BUILD SUCCESSFUL in 5m16s; JAR/JaCoCo generated.
  Full XML preserved in `build/verification/step-6-full/` before the independent
  focused retest, which replaces the default test-result directory.
- Fresh isolated HTTP retest then found one timing-sensitive test failure among
  eight: malformed-envelope check returned AI_TIMEOUT under the normal fixture's
  one-second budget. Known-key/null-model regression and intentional delayed-body
  timeout passed. Preserved failure XML at
  `build/verification/step-6-http-retest-failure.xml`.
- Separated normal HTTP contract test budget (five seconds) from the unchanged
  intentional one-second deadline/three-second assertion. All production limits,
  malformed/overflow/no-retry checks remain unchanged; an envelope case index
  improves diagnostics. Fresh verifier's isolated corrected HTTP retest passed
  **8 tests, zero failures/errors/skips**, BUILD SUCCESSFUL in 27s. Dedicated
  one-second delayed-body test passed in 1.095s within the unchanged three-second
  assertion.
- Final full rerun after the fixture correction: **112 tests across 14 suites,
  zero failures/errors/skips**, BUILD SUCCESSFUL in 5m50s; JAR/JaCoCo generated.
  Final full XML copied to `build/verification/step-6-full/`. No source changes
  after that run began. Final diff --check passed.
- `git diff --check`: passed during evidence preparation. V1–V5/build dependency
  files unchanged.

HTTP contract tests use a loopback fake provider with the real JDK adapter,
synthetic credentials and injected test-only URI/client constructor. Runtime
has no insecure URL flag. Tests cover no startup I/O, discovery success/cache/
missing/malformed/status failures/backoff, exact header/payload, status mapping/
retry ceiling, malformed/truncated/tool/oversized envelopes, exact response-byte
limit, delayed response-body deadline, non-forwarded redirects and missing/
invalid usage. No external traffic is used by these tests.

The PostgreSQL test renders create and owned OPEN-edit panels, requests a fake
suggestion with zero business writes, explicitly submits reviewed/edited fields
through ordinary creation, then proves assignment before ordinary edit rejects
stale version and a forged AI-accepted flag. Existing account/ownership/CSRF/
rate/freshness and mutation regressions remain in the suite.

Internal inspection then identified a configuration edge case: a model setting
containing the known API key could be recorded as model metadata before discovery
rejects it. Added a fail-closed availability guard and a regression asserting
unavailable/null model with no network. The parent started a final full rerun;
fresh verifier `/root/verify_review_transport` was invoked under AGENTS.md to
inspect that fix and connected code/results, then run an isolated local HTTP
suite with only test-generated build/cache writes allowed. Its timing finding
and correction are recorded above; production deadline assertions remain intact.
See `logs/handoffs/2026-10-16-requester-step-6-verification.md` for its
outcome. Human teammate review remains pending.

## Remaining work and next checkpoint

Live non-production smoke verification was explicitly deferred. No claim is made
about real provider model availability, response shape/support, token totals,
latency, draft quality or portal reconciliation. The implementation is locally
verified against deterministic fakes, not a model-injection immunity guarantee.

Browser/keyboard/focus/viewport/accessibility, hosted V4/V5/application, full
Requester→Manager→Technician lifecycle, 50-user performance and deployment
checks remain pending. DOM mocks and MockMvc do not replace those checks.
Discarding locally may still allow a bounded server invocation to finish and
consume quota; its result is ignored and cannot mutate business state.

Next user step 7 is plan section 9: integration/security/usability verification
within existing authorization, collaborating with role owners for missing role
services/environments. Preserve the explicit deferral of live checks until the
user changes it. Teammate review is required before non-trivial merge. Step 6
remains uncommitted; no push/merge/deployment authorized.

Skills: Ponytail, Context7 (Jackson 3), FacilityFlow handoff. Official JDK 25
HttpClient/timeout/body-subscriber references informed bounded HTTP handling;
see the Developer Guide link. No library dependency added.
