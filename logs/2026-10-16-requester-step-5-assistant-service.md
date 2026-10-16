# Requester step 5: Smart Report service against a fake gateway

Date: 16 October 2026 (Asia/Singapore). Branch: `feat/requester-workflow`.

## Authorization, scope and sources

The user requested committing step 4 and continuing step 5. Step 4 was committed
as `66b970c` (`feat(requester): add dashboard and discovery filters (REQ-RQ-001/003)`).
Step 5 remains uncommitted at this checkpoint. No push, merge, hosted database
change, deployment or live provider request was performed.

Authoritative baseline v0.1: `workflow/specs/ROLE-REQUESTER.md`, `PRODUCT-SPEC.md`,
`TECHNICAL-SPEC.md`, `INTEGRATION-SPEC.md` and `AI-SECURITY-SPEC.md`.
The next bounded task is section 7 of `workflow/REQUESTER-PLAN.md`.
Scope: REQ-RQ-012–014/016/017, REQ-SH-018, shared typed gateway/failure/metadata
contracts and relevant AI-SEC controls. Review UI and live HTTPS/model readiness
remain the next checkpoint; the other two role assistants are not implemented.

No gateway/fake implementation existed beyond `AiAccessGuard`. Added the minimal
shared seams needed by Smart Report; no real credentials/model or completed
teammate adapter assumed. Gateway ownership remains with `ngkhengyang`.

## Implemented contracts and behavior

- `ai/SmartReportInput`: immutable current unsaved fields, optional incomplete
  form values within normal maxima, trimmed text and validated optional enum/UUID.
- `SmartReportService`: persisted active Requester/session checks; owner, OPEN
  and expected-version checks for edit; admission before category/context load;
  minimal label-only context; fresh access/version/category checks after response.
  All database transactions finish before provider use. No business-write call.
- `AiGateway` and `AiTransport`: typed feature/actor/correlation/context/result
  boundary and server-only raw adapter seam. Model input omits actor/correlation
  IDs. Runtime adapter is unavailable; enabling AI cannot activate the test fake.
- `SmartReportSchema`/`ValidatedAiGateway`: source-controlled smart-report-v1
  prompt and one JSON-serialized provenance-labelled data message, fixed rules,
  aggregate limit, strict typed parser, Unicode bounds and fail-closed disclosure
  checks. Rejects malformed/duplicate/trailing/unknown/wrong-type fields, invalid
  category/urgency, empty optionals and missing required structure. Plain-text
  checks reject markup/links, unsafe controls, identifiers, prompt markers,
  known secrets and adapter canaries. Decoded-value checks handle JSON escaping.
- Bounded gateway: at most 800 requested output tokens, 30-second original
  deadline and five workers without a queue. Cancels/ignores late output; one
  retry for network/503 or a short deadline-fitting 429 Retry-After. No retries
  for 400/401/403, malformed/unsafe output or cancellation. Retry count survives
  timeout after a retry. No raw exception/provider body reaches the result.
- `AiOperations`/`JdbcAiOperations`: existing V3 rolling timestamp buckets and
  global slots enforce five invocations/minute, 30/hour and five in flight across
  instances. Short locked account admission, SKIP LOCKED slot claim, expiring
  lease and versioned release. One invocation quota unit covers its permitted
  retry. Configured limits can reduce approved defaults, never exceed them.
- Metadata-only insertion: actor/correlation, feature, model/template, status,
  latency, retry count and provider usage; unknown usage stays null. Known usage
  remains when a freshness/catalogue check discards output. No prompt/output,
  free-text report or business Audit Event is written. Metadata storage failures
  cannot return success.
- `SmartReportController`: role-protected POST create/edit suggestion routes with
  existing CSRF, no-store JSON, safe categories/messages and no business writes.
  Ordinary manual form remains independent; review/apply/discard UI is pending.

No migration, application dependency, live transport or role workflow change.
Existing V1–V5 remain unchanged and required. Guides, traceability, progress,
reuse and handoff records were updated. No MP2 source consulted/copied.

## Executed verification

1. `gradlew.bat compileJava`: BUILD SUCCESSFUL.
2. Focused `gradlew.bat test --tests '*SmartReportGatewayTest' --tests
   '*RequesterRequestIntegrationTest' bootJar`: initial **37 tests passed**,
   BUILD SUCCESSFUL in 1m26s.
3. Expanded focused run: **40 tests, one failed Mockito verification**,
   BUILD FAILED in 1m34s. The no-catalogue test attempted to spy on a Spring
   repository proxy and `verify` raised `NotAMockException`; the other 39 tests
   passed. Replaced that wrapper with an interface mock using
   `AdditionalAnswers.delegatesTo(real repository)`. Production queries and
   security assertions were preserved. No test weakened or removed.
4. Corrected focused rerun: **40 tests, zero failures/errors/skips**,
   BUILD SUCCESSFUL in 1m28s; JAR/JaCoCo generated. Fresh verifier
   `/root/verify_smart_report` independently read XML: nine gateway and 31
   genuine PostgreSQL integration tests, including corrected no-catalogue,
   rolling/global/CAS leases and in-flight revocation/catalogue cases.
5. Final full `gradlew.bat test bootJar`: **103 tests, zero failures/errors/skips**,
   BUILD SUCCESSFUL in 4m34s. JAR/JaCoCo generated. Counts read from all 13
   `build/test-results/test/TEST-*.xml` suites, including 10 gateway and 31 real
   PostgreSQL workflow tests. The JSON-escaped known-secret regression passed.

Tests use synthetic data with a test-only fake and actual disposable PostgreSQL
restricted app role. Coverage includes schema minima/maxima, Unicode, invalid
types/unknown fields/enums, literal JSON injection data, hidden/unauthorized
canaries, known secrets, no-queue concurrency, timeout/cancellation, retry ceiling,
all mapped failures, missing usage, persisted-account/role/session/owner/state/
version denials, CSRF, forged actor/accepted values, no context/provider after
denial/input/quota failure, in-flight revocation/assignment/category changes,
rolling minute/hour quotas, concurrent admission, expired lease recovery and
stale release protection. Suggestions leave Request rows/versions, submissions,
Requester Updates and business audit counts unchanged.

Fresh verifier inspected the corrected test wrapper and connected source,
transactions, SQL, schema, metadata and decoded-secret disclosure fix; reported
no blocking findings within step 5 scope. It independently confirmed all 103
tests across 13 final XML suites, including the corrected wrapper and escaped-
secret regression. See `logs/handoffs/2026-10-16-requester-step-5-verification.md`.
`git diff --check` passed during documentation preparation. No JavaScript changed;
the earlier Node self-check was not repeated.

## Limits, remaining work and continuation

Fake tests prove deterministic contracts, not live-model resistance to every
injection or semantic/obfuscated disclosure. The detector covers known patterns
and values, not all possible confidential content. Live transport must honor
remaining deadlines/interruption and add server-only URL/key/model wiring and
GET /models readiness; model validation must never block core startup. Portal
usage reconciliation, Manager operational aggregates and other assistant schemas
remain with their owners. No live token/latency/usefulness claims are made.

Next step 6 is plan section 8: labelled review UI in create/eligible edit, explicit
individual acceptance/edit/discard, no silent overwrite or automatic submission,
manual-path failure behavior, ordinary deterministic submission and coordinated
live SoCLaaS adapter/readiness/synthetic smoke evidence. Resolve material interface
or specification ambiguity before implementation; no credentials assumed.

Browser/keyboard/viewport/accessibility, hosted V4/V5/application, full cross-role
integration, deployment and performance verification remain pending. Human
teammate review is required before non-trivial merge. Step 5 is uncommitted.

Skills: Ponytail, Context7 (Jackson 3 strict parsing/builder documentation),
PostgreSQL best practices (short transactions/SKIP LOCKED) and FacilityFlow handoff.
Fresh verifier invoked under AGENTS.md after a failed-test correction; no other
implementation delegation occurred.
