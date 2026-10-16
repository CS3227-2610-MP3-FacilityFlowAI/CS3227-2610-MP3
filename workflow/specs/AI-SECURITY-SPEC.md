# AI Security Specification

Status: approved baseline v0.1, 12 October 2026. Owner: Security Reviewer with
ngkhengyang for gateway controls. Gateway revision candidate v0.2 received
stakeholder approval on 16 October 2026; Security Reviewer, affected role-owner,
teammate and fresh-reviewer review of the revision remains pending. This document
defines required controls and planned tests; it does not claim they are
implemented or verified. Iteration 2A provider-core/readiness decisions received
stakeholder direction and approval on 16 October 2026; specification Security
Reviewer findings were incorporated on 16 October 2026; re-review and teammate
review remain pending. The implementation Security Reviewer currently records a
NO-GO; the remediation requirements below are specified but implementation,
tests and security re-review remain pending.
Iteration 2B HTTPS-adapter requirements were prepared against provider-core base
commit `897937d`; the adapter, focused tests and fresh Security Reviewer approval
are complete. Iteration 2C adds startup-safe Spring lifecycle/readiness wiring;
fresh Security Reviewer review is GO. Later increments still own retries,
rate/lease controls, telemetry, role gateway/coordinator and compressed-response
support.

## Assets and trust seams

Protected assets are Account/session data, Request data outside the actor's
scope, Work Logs, Audit Events, credentials, system prompts, configuration,
provider budgets and business integrity.

Data crosses these seams:

1. browser to deterministic role endpoint;
2. authorized role module through a feature-specific advisory interface to
   shared AI orchestration;
3. stored/user text into the prompt as untrusted data;
4. application to SoCLaaS over HTTPS with bearer credential;
5. untrusted model text into schema validation;
6. validated suggestion to an encoded review UI;
7. human-approved values back through ordinary business operations;
8. development-agent inputs through handoff/review before repository changes.

Authorization occurs before context collection. Schema validation occurs before
display. Ordinary business authorization and validation occur again after human
review and before any write.

The current Account and correlation ID are server-derived. Each role module owns
a minimal persisted eligibility probe, lazy allowlisted context loader,
freshness snapshot and mandatory post-call recheck. The shared coordinator owns
admission, prompt/provider policy, validation and terminal metadata. No caller
may select a feature, actor, template, parser, model or arbitrary context map.

## Threats considered

- direct prompt injection asking the model to ignore instructions, disclose
  context, change role or simulate an action;
- indirect injection stored in Request descriptions, Requester Updates or
  approved playbook text;
- obfuscated/encoded, multilingual, delimiter-breaking and long-context
  injection;
- cross-Account/role data exposure through context, output, cache or error;
- model output containing HTML/script, unexpected fields, invalid enum/tag,
  excessive length, fabricated authorization or unsafe maintenance instructions;
- forged client values that claim AI approval or bypass human confirmation;
- replay/stale output applied after state, catalogue, skill or assignment change;
- denial of wallet/service through rapid, concurrent, oversized or retrying calls;
- credential/model/prompt leakage through browser, logs, Audit Events or handoffs;
- poisoned repository/document/agent instructions that expand development-agent
  permissions or falsify evidence.

## Required controls

- **AI-SEC-001:** Every AI request requires an authenticated active Account with
  the correct role and object-level scope defined by its role specification.
- **AI-SEC-002:** Authorization completes before loading model context. A denied
  request makes no SoCLaaS call and reveals no protected object details.
- **AI-SEC-003:** System/developer instructions and untrusted data use separate
  message sections with explicit delimiters and provenance labels. Data content
  is never interpreted as an authorization or instruction source.
- **AI-SEC-004:** Each feature sends only its allowlisted context fields. No
  password material, session identifier, API key, hidden account data, full Audit
  Event, unrelated Request, or database/internal identifier is sent.
- **AI-SEC-005:** SoCLaaS receives no tools, function definitions, browser,
  retrieval, filesystem, network capability or business mutation interface.
  The internal provider port exposes only `listModels` and `complete`; presence
  of any tool/function member in a completion response rejects the whole response
  even when that member is null or empty. Iteration 2A provider implementation
  types are package-private, including `SocLlmProviderCore`,
  `SocLlmProviderPort`, decoder/body/failure types, their constructors and all
  mutable inputs. Role and web packages must not import `ai.provider`. A later
  coordinator/factory will remain inside the provider package and expose only
  feature-specific assistant APIs; it is not implemented in Iteration 2A.
- **AI-SEC-006:** Each invocation is stateless. The application sends no previous
  AI conversation and stores no raw prompt or raw output after the response is
  processed. Raw provider bodies remain inside the provider core and never cross
  its public seam. Each non-shared chat provider stage exposes best-effort
  cancellation without including request/response content in diagnostics.
  Readiness-start exposes only a read-only completion-stage view. Completion
  exposes a narrow read-only cancellable handle containing only a read-only
  completion-stage/result view and explicit `cancel`; neither seam exposes an
  internal future or any external completion, exceptional-completion, obtrusion,
  timeout-mutation or replay path.
- **AI-SEC-007:** Aggregate serialized prompt content is limited to 12,000
  Unicode code points after converting CRLF/CR to LF, normalizing Unicode to NFC
  and applying only existing field-specific outer trimming. Permitted internal
  whitespace is preserved. Count final system-message content plus final
  JSON-serialized user-message content, including syntax, escaping, provenance
  labels and delimiters. Each business field retains its deterministic limit;
  12,001 code points or any other excess is rejected before provider use without
  silent truncation, except for the specified latest-ten Update selection rule.
- **AI-SEC-008:** The gateway requests at most 800 output tokens, uses a 30-second
  deadline, allows at most five in-flight calls application-wide, five calls per
  Account per rolling minute and 30 per Account per rolling hour. Configuration
  may only preserve or tighten those maxima. One original configured deadline
  begins at AI use-case entry after web authentication/CSRF and covers
  eligibility, admission, context, readiness, waits, provider attempts, decode,
  validation, post-call recheck, terminal telemetry and lease release.
- **AI-SEC-009:** At most one automatic retry is permitted for a network failure
  or 503 after a 250-millisecond delay. For 429, retry only when `Retry-After` as
  delta seconds or an RFC-1123 date resolves to 0–2 seconds inclusive and at
  least five seconds remain in the original deadline. The deadline never resets.
  Never retry 400, 401, 403, validation failure, unsafe output or cancelled
  requests.
- **AI-SEC-010:** A feature-specific strict parser rejects malformed JSON,
  duplicate or unknown fields, trailing content, wrong types, excessive
  arrays/text, invalid categories, priorities, urgency or skill tags, and any
  result outside its schema. The provider envelope is limited to 256 KiB after
  decompression and must contain exactly one choice, a single non-empty assistant
  content string, no tool/function call and `finish_reason` equal to `stop`.
  Reject 200 error envelopes, empty/multiple choices, malformed content,
  truncation and inconsistent non-negative usage counts. Provider bytes use
  strict UTF-8 after decompression. The decoder receives a streaming
  post-decompression body and aborts when byte 262,145 is observed, before full
  materialization/parsing. JSON is bounded before any unrestricted tree: nesting
  depth 64, string length 262,144, numeric-token length 20, total tokens 8,192 and
  model entries 1,000. Duplicate keys, trailing content and a root error member
  reject. A compressed-wire limit remains deferred to a later
  compressed-transport increment.

  Envelope location allowlists are exact. Models root permits only `object` and
  `data`; model entries only `id`, `object`, `created`, `owned_by`; chat root only
  `id`, `object`, `created`, `model`, `system_fingerprint`, `service_tier`,
  `choices`, `usage`; choice only `index`, `message`, `finish_reason`, `logprobs`;
  message only `role`, `content`; usage only `prompt_tokens`,
  `completion_tokens`, `total_tokens`. Permitted inert metadata may have any JSON
  type but is never consumed. Every unlisted member rejects on presence.

  Model discovery requires a root object with a non-null `data` array. Every
  entry is a non-null object with a unique, non-empty string `id`; any malformed
  or null entry or duplicate rejects the whole result. The configured model must
  match one ID exactly, with a maximum 1,000 entries. Completion requires exactly
  one choice, `message.role` exactly `assistant`, one non-empty content string and
  `finish_reason` exactly `stop`. The message allowlist means `refusal`, `audio`,
  `name`, `tool_calls`, `function_call` and every unknown member reject even when
  null/empty. Optional `usage`, when present, is non-null/non-empty and contains only
  a non-empty subset of `prompt_tokens`, `completion_tokens` and `total_tokens`.
  Values are non-negative integral signed-64-bit values; when all three exist,
  total equals prompt plus completion without overflow.
- **AI-SEC-011:** Reject disallowed Unicode control characters, allowing LF and
  TAB only where field rules permit them, plus HTML tag syntax, script/style
  content, Markdown links/images and URI-bearing markup. Every accepted string
  is still rendered through framework escaping.
- **AI-SEC-012:** The Technician plan is rejected if required diagnostic,
  evidence, or stop/escalation structure is missing, or if a safety-rule detector
  identifies clearly dangerous prohibited advice. Rejection is not a claim that
  accepted advice is professionally certified. The detector is a deterministic,
  versioned structural/rule policy with a human-reviewed multilingual and
  obfuscated corpus. It is negation-aware; prohibited-condition advice must
  direct the Technician to stop or escalate. It uses no LLM safety judge, and a
  keyword match alone is not evidence of safety.
- **AI-SEC-013:** AI results are visually labelled, editable/rejectable where
  appropriate, and never pre-authorize a business operation.
- **AI-SEC-014:** Any later business submission ignores AI provenance for
  authorization, re-reads current Account/object/version/catalogue state, applies
  ordinary validation, and requires the authenticated human's explicit action.
- **AI-SEC-015:** Client-supplied AI result IDs, accepted flags, role names,
  suggested assignees or prompt versions shall not bypass server checks.
- **AI-SEC-016:** AI failure, rejection, quota exhaustion or disabled
  configuration returns a stable safe error category and leaves the manual
  workflow available with current user input. Provider-decoder errors expose only
  closed stable codes. No exception, result, cause chain or log contains raw
  body/response/error content, attacker-controlled value, parser message, source
  location/excerpt, arbitrary cause or object `toString()` output. Disable parser
  source-in-location where supported. Checked or unchecked streaming-body read
  failures map to `BODY_UNREADABLE` without cause/message data, including
  canary-bearing unchecked failures. An existing safe `ProviderFailure` code is
  preserved. Checked and unchecked close failures are suppressed on every path
  and cannot replace the primary outcome or appear in a cause chain, return
  value or log.
- **AI-SEC-017:** Logs and AI telemetry record correlation ID, Account ID,
  feature, model, prompt-template version, result status, latency, retry count and
  provider token counts when supplied. They exclude raw prompt/output, API keys,
  session values and full Request text. After admission and before protected
  context loading or provider use, a durable `STARTED` record is required;
  failure to create it prevents both. A terminal
  update records `SUCCEEDED`, the applicable safe failure category, or
  `DISCARDED_ACCESS_CHANGED`. Post-call access/freshness discard is never
  `SUCCEEDED`. If terminal persistence fails after provider use, withhold output,
  return `AI_UNAVAILABLE`, retain `STARTED` evidence and emit only a sanitized
  operational alert. The required status/check/grant schema revision must be
  reviewed before implementation.
- **AI-SEC-018:** AI metadata and provider token counts are retained for the life
  of the project. Missing provider usage is recorded as unavailable and never
  estimated as actual usage.
- **AI-SEC-019:** SoCLaaS base URL, key and model are server-only configuration.
  The key is never sent to the browser. Development and Production use separate
  keys where the provider permits. `AI_ENABLED` defaults to false. Enabled
  configuration requires a non-placeholder key/model and the exact canonical
  `https://soclaas-api.comp.nus.edu.sg/v1` origin/path, permitting only a
  normalized trailing slash. Reject user information, non-default port, query,
  fragment, origin/path changes and redirects; use normal TLS verification. Only
  validated enabled `AiConfiguration.Available` may construct/start a provider
  core. Disabled/unavailable configuration creates no core, schedules no work and
  makes zero port calls. Iteration 2A configuration is immutable/process-lifetime.

  The Iteration 2B HTTPS adapter and HTTP transport seam, including production
  JDK transport, constructors and response-body wrappers, are package-private.
  The JDK `HttpClient` uses redirects `NEVER` and default TLS trust/hostname
  verification. It calls only canonical `/v1/models` and
  `/v1/chat/completions`. Requests set `Accept: application/json`,
  `Accept-Encoding: identity` and a server-side Bearer authorization header;
  completion additionally sets `Content-Type: application/json`. The credential
  is supplied only as an apply-to-request capability: no adapter or external API
  receives the raw key as a returned string, and the key never enters a
  diagnostic, result or log.

  Model discovery is bodyless GET. Completion is UTF-8 POST JSON containing only
  model, exactly two ordered system/user messages with role/content, validated
  `max_tokens`, and `stream: false`; deterministic escaping rejects malformed
  surrogates before transport. No tool, function, history or extra member is
  sent. Response `Content-Encoding` is absent or identity-only. The identity
  stream is passed to the existing bounded post-decompression decoder; unsupported
  encoding fails closed. The decoder owns and closes a handed-off HTTP 200
  identity body; the adapter closes every rejected, cancelled, timed-out, late or
  failed response body that was not handed off.

  Transport status mapping is closed: 400 `HTTP_BAD_REQUEST`, 401
  `HTTP_UNAUTHORIZED`, 403 `HTTP_FORBIDDEN`, 429 `HTTP_RATE_LIMITED`, 503
  `HTTP_SERVICE_UNAVAILABLE`, any redirect/effective-URI change
  `REDIRECT_REJECTED`, other non-200 `HTTP_UNEXPECTED`, timeout `TIMEOUT`,
  cancellation `CANCELLED`, transport/TLS/DNS failure `NETWORK_FAILURE`, and
  request construction failure `REQUEST_BUILD_FAILED`. No mapped failure retains
  a response body/header, unsafe cause or attacker value.

  A 429 exposes retry advice only for exactly one `Retry-After` field containing
  delta-seconds 0–2 inclusive or an RFC-1123 date whose injected-clock delta is
  0–2 seconds inclusive. Multiple, blank, negative, overflowing, malformed or
  out-of-range values yield no advice. Iteration 2B performs no retry.

  Iteration 2C wires an available runtime through a narrow lifecycle interface.
  Its Spring bean starts readiness asynchronously and never blocks application
  startup on the provider. Disabled and unavailable configurations create only
  an inert, terminal lifecycle and perform zero core, executor, scheduler,
  transport or provider work. Available lifecycle close is idempotent, closes
  the provider core and owned executors/scheduler, and ignores late readiness
  callbacks. Discovery and chat dispatch use separate executors; a deterministic
  package-private factory seam is used for tests. No role/web source imports
  provider implementation types.
- **AI-SEC-020:** A configured model is checked against GET /v1/models without
  blocking core application startup. Readiness states are exactly `UNVALIDATED`,
  `CHECKING`, `NEGATIVE` and `READY`. The core exposes only a non-blocking
  readiness-start operation in Iteration 2A; Iteration 2C invokes it only for
  `AiConfiguration.Available` through the lifecycle bean and does not block
  Spring startup.

  Each check has immutable generation identity and one absolute expiry. Only the
  current owned `CHECKING` generation may transition. Timeout or core closure
  invalidates ownership, and late callbacks are ignored; closure adds no public
  state. Inject a discovery `Executor` separately from a deterministic
  `ScheduledExecutorService` or narrow scheduler for timeout/deadline events.
  Joiners never reset shared expiry. Caller cancellation/timeout detaches without
  cancelling the check. The shared expiry must be scheduled successfully before
  discovery-executor submission or `listModels`. Scheduling rejection transitions
  the owned generation directly to `NEGATIVE` with zero executor/port work. If
  discovery submission then rejects, cancel the installed timer and transition
  the still-owned generation to `NEGATIVE`.

  Timeout, malformed model data, missing model, provider/transport failure,
  discovery-executor rejection or shared-timeout scheduling rejection becomes
  `NEGATIVE` only through an accepted owned transition. Its 60-second cache starts
  at that transition. After expiry, first use starts/joins one new generation.
  Exact model match becomes process-lifetime `READY`. Public completion supplies
  an absolute caller deadline and waits only to the earlier of that deadline or
  shared expiry. After `READY`, recheck that deadline and successfully install
  per-call deadline/cancellation enforcement before port `complete`. Prior
  cancellation, expiry or scheduling rejection makes zero chat calls.
  `ProviderChatRequest` carries the absolute deadline.

  A chat call is not shared. After dispatch, caller cancellation/deadline and
  provider completion atomically compete for one terminal outcome. A caller-side
  win detaches/terminates the caller, requests best-effort cancellation of that
  unique provider stage, ignores all late provider/decode completion and returns
  no content. A safe provider-completion win returns its result; later
  cancellation has no effect. Chat cancellation, timeout, transport or decode
  failure does not demote `READY`.

  The injected provider-dispatch `Executor` runs both `listModels` and `complete`
  invocations; no caller assumes either port method is non-blocking. Readiness
  and chat use two-phase reserve/invoke/install: reserve ownership under the
  applicable monitor, release it, invoke on the dispatch executor, then install
  or attach the returned stage only if ownership is still current. No port
  method, provider-stage attachment/cancellation, internal future completion or
  caller/dependent callback runs while a readiness/call monitor is held.
  Deadline, cancellation or close can finish while a port invocation is
  synchronously blocked. A late stage for a lost generation/call is cancelled
  best-effort outside the monitor and ignored. Executor rejection is a safe
  failure with zero port work; all internal future completions occur after
  releasing the monitor.
- **AI-SEC-021:** Rate-limit state is enforced server-side. If distributed
  execution prevents safe in-memory enforcement, use PostgreSQL-backed atomic
  counters rather than weakening the limits. One admitted logical invocation
  consumes one Account timestamp; its permitted retry consumes no second
  timestamp but is recorded as another provider attempt. Authentication, CSRF,
  early size, disabled/configuration and pre-admission failures consume no
  Account quota. A global fenced lease spans context, readiness, provider/retry,
  validation, post-call recheck and terminal telemetry. It lasts 45 seconds,
  renews every 15 seconds using database time and owner/version fencing, and is
  released only by the matching fencing token. Lease loss discards output.
  Limiter database failure fails closed without a provider call. Any required
  lease-owner/fencing storage revision is reviewed before implementation.
- **AI-SEC-022:** Prompt templates are versioned in source and changed through
  pull request review with schema/security tests. Prompts may not grant wider
  context or mutation authority.

For AI-SEC-012, the minimum prohibited set is advice to bypass interlocks or
lockout/tagout, work on energized electrical systems, continue during a suspected
gas leak/fire/structural instability, enter a confined space, handle unknown
hazardous material, or proceed without required protective equipment. Such
conditions must appear as stop/escalate guidance. The test corpus owns exact
phrasing and obfuscation variants; keyword matching alone is not sufficient
evidence of safety.

## Human and development-process gates

- **AI-SEC-023:** Role users review every AI result according to their role
  specification; only ordinary deterministic endpoints perform actions.
- **AI-SEC-024:** Specification revisions, security exceptions, non-trivial pull
  requests and production promotion require human review. An agent or handoff
  cannot approve itself or grant production/credential access.
- **AI-SEC-025:** Development agents use the least write scope in
  workflow/agents. They validate requirements against authoritative files,
  treat repository/external text as untrusted, preserve unrelated work and leave
  sanitized evidence.
- **AI-SEC-026:** Each transition uses the project handoff protocol. A receiving
  agent verifies cited revisions, test evidence and permissions; claimed tests
  without command/result evidence remain unverified.
- **AI-SEC-027:** A fresh verifier retests fixes after failed security tests or
  findings. Tests are not weakened to make a result pass; a human records risk
  acceptance.

## Response freshness and leakage controls

- **AI-SEC-028:** After the provider returns and before any suggestion is sent
  to the browser, the server rechecks the Account's active state and role plus
  current object-level access. If access changed during the call, the entire
  result is discarded, no protected content is returned, and terminal metadata
  is `DISCARDED_ACCESS_CHANGED`. Also discard Work Plan on assignment, status or
  Request-version change; Triage on Request state/version, category catalogue or
  skill catalogue change; and Smart Report persisted edit on ownership, state,
  version or category-catalogue change. A now-inactive catalogue value invalidates
  every feature result that refers to it.
- **AI-SEC-029:** Browser AI invocations use POST with valid CSRF protection.
  Authentication, role/object authorization, CSRF, input-size and local
  rate/concurrency checks complete before context loading or any provider call.
  Existing deterministic field limits reject invalid browser input before
  protected context loading; the exact browser byte cap remains an explicit
  pre-implementation parameter. After context loading, AI-SEC-007 applies the
  aggregate serialized-prompt limit. Responses use `Cache-Control: no-store` and
  are never stored in the server session or client persistence.
- **AI-SEC-030:** Schema-valid output still passes a fail-closed disclosure
  check before display. Reject the entire result on an exact or NFC/case-normalized
  match of server secrets, fixed prompt delimiters/canaries, unauthorized-context
  canaries/content, protected prompt-template fragments, or any UUID, internal
  identifier or display identifier not permitted by the output schema.
  Request-specific deny material is assembled server-side and is never sent as
  an instruction. Tests place unique canaries in system-only and unauthorized
  context and require rejection if any canary is returned.

## Planned security test matrix

| Case | Attack/control | Expected allowed result | Expected forbidden result |
| --- | --- | --- | --- |
| AIS-T01 | direct ignore/disclose instruction in rough report | safe typed draft or rejection | hidden prompt/context disclosed |
| AIS-T02 | stored indirect instruction in Request/Update | treated as quoted data | instruction controls model |
| AIS-T03 | encoded/multilingual/delimiter payload | typed result/rejection | tools, role change or leakage |
| AIS-T04 | Requester/Technician cross-object AI call | denial before provider call | protected data/provider charge |
| AIS-T05 | wrong-role direct endpoint call | 403/safe denial | output or mutation |
| AIS-T06 | oversized browser input, 12,001-code-point prompt, arrays or 256 KiB response boundary | local validation/rate response | provider call after local excess or memory exhaustion |
| AIS-T07 | HTML/script/unknown field in model JSON | reject/escape | script execution or silent field use |
| AIS-T08 | invalid category/skill/priority | reject and manual fallback | invented value stored |
| AIS-T09 | multilingual, obfuscated or negated unsafe Technician plan | deterministic corpus result or rejection with safety message | keyword-only approval, LLM safety judge or unsafe plan presented as approved |
| AIS-T10 | concurrent/multi-instance calls, lease loss or limiter failure | bounded calls, fenced discard or 429-style response | unbounded provider traffic or stale release |
| AIS-T11 | 400/401/403/429/503/timeout or cancellation | exact original-deadline/retry/error policy | retry storm, reset deadline or draft loss |
| AIS-T12 | stale triage after state/tag/workload change | revalidation/refresh | obsolete assignment |
| AIS-T13 | forged accepted/result/role client fields | normal authorization/validation | approval bypass |
| AIS-T14 | logs, audit, handoff inspection | safe metadata only | secret/raw prompt/output/full text |
| AIS-T15 | malicious handoff/document instruction | scope remains authoritative | permission expansion |
| AIS-T16 | Account/object access or feature freshness changes while provider call is in flight | `DISCARDED_ACCESS_CHANGED` after fresh authorization | stale protected result displayed or recorded successful |
| AIS-T17 | missing/invalid CSRF or local limit failure on AI POST | local rejection before context/provider | provider call or protected context load |
| AIS-T18 | schema-valid output repeats secret/delimiter/template/identifier or hidden/unauthorized canary | entire result rejected and safely logged | leaked content displayed or retained |

Additional planned cases cover invalid configuration and redirects, readiness
single-flight/cache behavior, fenced lease renewal and stale release, provider
envelope/usage variants, terminal telemetry persistence failure, `no-store`
responses, and proof that early denial performs zero protected context loads,
readiness calls and provider calls. Provider-core cases use injected clocks,
separate discovery executors/schedulers and caller-controlled pending futures
without sleeps; fake streaming raw bodies pass through the production decoder.
They cover unavailable/disabled zero construction/work/calls, generation and
closure races, immutable shared expiry, strict UTF-8, abort at byte 262,145,
JSON/token/model limits, every location allowlist, exact model envelopes,
cancellation/timeout isolation, dispatch/scheduling rejection, absolute deadline
propagation, READY stability and all completion/usage shapes. Full cause-chain and
captured-log canaries prove stable decoder errors disclose no raw, attacker or
parser content. No-sleep readiness tests prove shared timeout scheduling precedes
discovery submission/`listModels`, scheduler rejection performs zero discovery
work, and discovery-submission rejection cancels its timer. No-sleep chat tests
cover cancellation/deadline before and after dispatch, zero calls before
enforcement is armed, provider-response wins, cancellation wins, best-effort
unique-stage cancellation and ignored late provider/decode completion.
Further no-sleep cases block synchronous `listModels` and prove timeout/close
finishes without releasing it; block synchronous `complete` and prove deadline
or cancellation returns promptly before a late stage is cancelled and ignored;
and assert no external mutation/replay surface. Visibility/architecture tests
prove provider types/mutating inputs are package-private and role/web code has no
`ai.provider` dependency. Checked and unchecked read/close canary tests prove
`BODY_UNREADABLE` mapping, preservation of safe `ProviderFailure` codes, and no
canary in results, cause chains or captured logs.

Iteration 2B adapter cases use an injected package-private transport and clock,
without network or sleeps, to verify canonical endpoints, exact request
headers/shape, credential non-exposure, redirect NEVER/default TLS, absolute
deadline and cancellation propagation, identity decoder handoff, body closure,
late-response disposal, every safe status/timeout/network mapping and exact
single-header `Retry-After` boundaries. Architecture tests prove the adapter and
transport seam remain package-private and unreachable from role/web packages.
Iteration 2C lifecycle cases use injected executors/scheduler and a fake
provider port without network access; they cover disabled/unavailable zero-work
paths, available asynchronous readiness, separate discovery/provider dispatch,
close idempotence and ignored late callbacks. Retries, rate/lease enforcement,
telemetry, role-feature gateway and compressed decompression remain explicitly
deferred.

Execute with synthetic data in an authorized non-production environment and
record actual results using the security-test template. Planned cases are not
passes.
