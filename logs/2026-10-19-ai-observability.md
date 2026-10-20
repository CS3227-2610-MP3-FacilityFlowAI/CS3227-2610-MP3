# AI observability implementation and simplification

- Date: 19 October 2026, Asia/Singapore.
- User direction: implement AI monitoring without the multi-agent workflow;
  subsequently simplify aggregation and tests around direct database queries.
- Scope: REQ-AI-007–009, AI-SEC-017–019, REQ-SH-002/009/010 and NFR-OPS-002.
- Process: one agent performed implementation, test authoring and verification.
  No delegation, merge, push, hosted database operation or live provider call.

## Resulting design

`JdbcAiInvocationTelemetry` records STARTED and exactly one final outcome in the
existing V3/V5 invocation table. All assistant orchestration paths, including the
older form-based Smart Report endpoint, use this lifecycle. Recording failure
prevents inference or withholds the suggestion. Successful output discarded after
access/source changes retains an appropriate non-success disposition. Recording
does not create maintenance data or a business Audit Event.

`AiMonitoring` is the single aggregation module. It checks the Facilities
Manager's persisted active state, role and session version, then queries
PostgreSQL directly in a consistent read-only transaction. The separate
monitoring repository and forwarding service were removed. Small immutable
filter/report/comparison records describe input validation and page data.
The controller handles HTTP and safe failures; the template displays aggregates.

The Manager page has inclusive Singapore date and assistant filters, invocation
outcomes, success rate among finished attempts, finished-duration median/p95,
retry totals, known token sums with per-field missing counts, unresolved STARTED
records, and aggregate model/prompt breakdowns. It exposes no individual
invocation, Account or Request identifiers. Readiness is a shared-gateway
instance snapshot; reading the page invokes no model discovery or completion.

A CSRF-protected comparison POST accepts independent portal token counts for a
bounded all-assistant period. It logs the portal/application snapshot and signed
differences without changing usage. Operators must use matching key/environment/
period data, compare periodically, and retain the operational event. No portal
automation or scheduled provider traffic is added.

Lifecycle diagnostics stay in the existing JDBC telemetry class; persistence
failures use the existing AI failure diagnostics. The additional diagnostics
wrapper was removed. Correlation generation lives in the request filter rather
than a separate helper class. JSON logs include version and safe typed metadata;
the invocation handle retains identifiers across asynchronous completion. Request
logging excludes URL, query, body and header values. Raw AI content and secrets
are not added to telemetry or diagnostic events.

## Tests and corrections

- Focused simplified run: `gradlew.bat --offline --no-daemon test
  --tests '*AiMonitoring*' --tests '*RequestCorrelationFilterTest'
  --tests '*RequesterRequestIntegrationTest' --tests '*SecurityIntegrationTest'`
  passed 94 tests across six suites, zero failures/errors/skips, in 2m14s.
- Five `src/test/js/*.test.cjs` suites passed with Node.
- `git diff --check` passed.
- The earlier implementation run exposed a final-class Spring repository proxy
  issue and test fixture timestamp permissions; both were corrected before the
  focused implementation checks passed.
- The earlier broad run completed 753 tests with one failure in a duplicate
  controller unit test: restubbing an already-throwing Mockito method invoked
  the prior exception. The simplification removes that extra test class and
  exercises validation/storage error handling through real MVC/security rendering
  instead. It also folds async metadata checks into the real PostgreSQL lifecycle
  test. Assertions for failure handling, missing usage, unfinished records,
  Manager authorization, CSRF, escaping and unchanged usage are preserved.
- Full simplified `gradlew.bat --offline --no-daemon check bootJar` passed in
  9m04s: 751 tests across 63 suites, zero failures/errors/skips; JaCoCo report,
  check and executable JAR packaging succeeded. Evidence is under
  `build/ai-monitoring-simplified-check.log` and `build/test-results/test`.
- Four startup log events parsed as JSON, each with `deployment_version`.
- Compared with the initial monitoring design, three added production classes
  and two standalone test classes were removed. Aggregation and authorization
  are tested together through AiMonitoring; web errors/access/CSRF are checked
  through the existing SecurityIntegrationTest rather than an extra controller
  unit-test fixture.

## Limits and review

No browser, live-provider or deployment acceptance is claimed. Original historical
telemetry rows are preserved and may include pre-admission attempts recorded by
the older path. Known usage may omit retries whose provider response had no
usage; incomplete coverage remains visible. Unresolved records are flagged,
never silently rewritten as timeouts. No new migration or dependency is needed.

README, User/Developer guides, specifications, traceability and reuse evidence
describe the simplified design. No MP2 source was consulted or copied. Review
of this single-agent implementation by a human teammate remains pending; no
independent-agent verification is claimed.
