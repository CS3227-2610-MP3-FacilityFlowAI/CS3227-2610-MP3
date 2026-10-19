# Requester live SoCLaaS smoke

- Date: 19 October 2026, Asia/Singapore; source revision d62cdce.
- User authorized live AI testing, superseding the earlier live-smoke deferral.
- Requirements: REQ-RQ-012–017, REQ-AI-001–006, AI-SEC-008/010/019/020.
- Scope: synthetic maintenance data through the actual SoClaasTransport and
  ValidatedAiGateway, plus bounded structural provider diagnostics. Locally saved
  credentials stayed in process memory. No credential, raw prompt/output or
  provider error body was printed or saved. No database connection or business
  write occurred. This was not a live browser/service-authorization smoke.
- Five generation calls total, each capped at 800 output tokens and 30 seconds.
  Production gateway calls also performed their bounded model discovery.

## Results

1. Actual Requester gateway: ordinary synthetic report returned AI_INVALID_OUTPUT,
   no suggestion, 12,611ms, zero retries. Remaining scenarios stopped.
2. Structural diagnostic with baseline request settings: HTTP200, one assistant
   choice, finish=stop, final content present, SmartReportSchema.parse passed;
   usage 361 input / 750 output / 1111 total tokens. Raw content withheld.
3. Actual gateway repeat: AI_INVALID_OUTPUT, no suggestion, 13,010ms, zero retries.
   Instruction-injection scenario was not sent because the ordinary case failed.
4. Structural diagnostic using exact failing synthetic input: HTTP200, finish=length,
   final content absent, schema invalid; 362 input / 800 output / 1162 total tokens.
   Body remained within 64KiB; no non-null tools/function/refusal fields. This
   confirms token-budget exhaustion before a final answer for this sample.
5. Diagnostic control with the shared gateway's existing reasoning_effort=none:
   HTTP200, finish=stop, final content present, schema valid; 326 input / 179 output /
   505 total tokens. No production source change was made for this control.

## Assessment and limits

Connectivity and credentials work. The current Requester production path does
not pass reliable live smoke: it omits reasoning_effort=none, whereas the shared
SocLlmHttpsProviderAdapter already sends it. The successful control supports
aligning Requester transport with that established non-reasoning contract; it
does not prove all future model responses will be valid. Do not increase token
limits or relax truncation/schema rejection to bypass this failure.

Next: a focused transport fix plus exact-payload regression test, fresh verification
per AGENTS.md, then repeat ordinary/missing-detail/injection live cases and an
authenticated isolated application smoke. Other role assistants, hosted browser
paths, manual response-quality review and broader safety corpus remain untested
by this session. No fix, commit, push, database edit or local config edit performed.

Execution used ignored build/LiveAiSmoke.java and structural diagnostic helpers
with the packaged production classes. Two initial diagnostic launch attempts
failed before any provider request (source package path / classloader access);
compiling the ignored helper with the application classpath resolved the harness.

## Authorized fix and successful retest

User requested alignment and retest. Added reasoning_effort=none to the actual
Requester transport, matching the existing approved INTEGRATION-SPEC completion
contract and shared adapter. The exact-payload regression asserts five root
members and the none value. No limits, TLS/origin/redirect behavior, schema or
truncation rejection changed. No dependency or configuration edit was needed.

`gradlew --offline --no-daemon test --tests ...SoClaasTransportTest --tests
...SmartReportGatewayTest bootJar` passed in 31 seconds: 19 tests, zero
failures/errors/skips; JAR rebuilt. The ignored live runner's runtime was refreshed
from that rebuilt production JAR before retesting. Actual gateway results:

| Synthetic case | Status | Latency | Input/output/total tokens | Retries |
| --- | --- | --- | --- | --- |
| Ordinary report | SUCCEEDED, typed suggestion valid | 4165ms | 326/185/511 | 0 |
| Missing details | SUCCEEDED, typed suggestion valid | 2453ms | 320/162/482 | 0 |
| Untrusted instruction | SUCCEEDED, typed suggestion valid | 2593ms | 322/174/496 | 0 |

Three retest generation calls; zero database connections or business writes.
No raw content logged. The instruction case establishes that this sample passed
the existing typed/disclosure checks, not comprehensive injection resistance or
human-reviewed response quality. Full authenticated live browser/service testing,
other-role live checks and broader safety review remain pending. Full local suite
was not repeated for this narrow payload change; earlier merge evidence remains
historical. No commit or push performed.

Fresh merge_verifier independently reviewed the two-file fix after the live
failure: no blockers; payload and regression match INTEGRATION-SPEC and shared
adapter. It verified unchanged TLS/origin/redirect, model discovery, deadline,
response bound and strict finish/content/schema controls. Static review only;
root session performed the actual focused tests and live calls recorded above.

## Authenticated live endpoint continuation

User requested another live AI test. Extended scope to actual Spring MVC endpoint,
authentication/security filters, real JDBC sessions, production service/transport,
restricted app role and disposable local PostgreSQL migrated through V6. No fake
transport/service was registered. Local configuration supplied only the provider
key/model in memory; all datasource settings were fixed to the disposable local
instance. No shared Supabase access. Three live synthetic generation calls.

Anonymous POST was redirected to login; authenticated POST without CSRF and a
Manager-role POST were rejected403. None created AI invocation metadata. Real
Requester login created a JDBC session. With valid CSRF, endpoint results were:

| Synthetic case | HTTP/result | Endpoint latency | Recorded service latency | Input/output/total tokens | Retries |
| --- | --- | --- | --- | --- | --- |
| Ordinary report | 200/SUCCEEDED, suggestion present | 3700ms | 3529ms | 335/175/510 | 0 |
| Missing details | 200/SUCCEEDED, suggestion present | 3350ms | 3318ms | 331/203/534 | 0 |
| Untrusted instruction | 200/SUCCEEDED, suggestion present | 2448ms | 2415ms | 333/133/466 | 0 |

All three responses carried Cache-Control no-store. The isolated database held
exactly three metadata-only AI invocation rows. maintenance_request,
request_submission, requester_update and work_log counts remained zero. The
authenticated ordinary report form still rendered200 afterward. Temporary app
and database were closed. This verifies the authenticated create-assistant
endpoint with live AI, not browser interaction, the owned-edit endpoint, other
role assistants, response-quality certification or a full injection corpus.

Runner: ignored build/AuthenticatedLiveAiSmoke.java, using the existing test
runtime dependencies exported by an ignored Gradle init task. Safe check/metric
lines are in ignored build/authenticated-live-ai-smoke.log; no raw prompts,
responses or secrets logged. First launch failed before execution because of a
UTF-8 BOM in the ignored runner; correcting that encoding enabled the successful
run. Production code/configuration was unchanged in this continuation. No commit
or push performed; full suite was not repeated for test-only evidence updates.
