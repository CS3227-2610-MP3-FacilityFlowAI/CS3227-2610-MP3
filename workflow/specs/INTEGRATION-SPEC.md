# Integration Specification

Status: approved baseline v0.1, 12 October 2026. Owner: ngkhengyang. Gateway
revision candidate v0.2 received stakeholder approval on 16 October 2026;
Security Reviewer, affected role-owner, and teammate review of the revision
remains pending. This status is not an implementation or verification claim.
Business eligibility remains in role specifications; this document owns
transport and typed contracts. Iteration 2A provider-core/readiness decisions
received stakeholder direction and approval on 16 October 2026; specification
Security Reviewer findings were incorporated on 16 October 2026; re-review and
teammate review remain pending. The implementation Security Reviewer recorded
a NO-GO for then-open remediation findings; subsequent commits provide local
implementation/test evidence for selected controls, but security re-review and
teammate approval remain pending.
Iteration 2B HTTPS-adapter requirements were prepared against provider-core base
commit `897937d`; the adapter, focused tests and fresh Security Reviewer approval
are complete. Iteration 2C adds production lifecycle/readiness wiring and has a
fresh Security Reviewer GO. Later candidate commits also implement retries,
rate/lease controls and durable telemetry locally; these status notes do not
replace the pending approved specification/review update, and compressed-response
support remains deferred.
Iteration 2D adds the first role-facing Technician Work Plan vertical slice;
focused implementation tests pass, while teammate and fresh Security Reviewer
review remain pending. Iteration 2E adds a shared process-local admission
coordinator for configured in-flight and rolling per-account limits. Iteration
2F adds PostgreSQL-time Account buckets and fenced global leases. The candidate
includes later local retry and durable invocation telemetry evidence; the
approved revision still requires synchronized review before those controls can
be treated as approved scope.

## SoCLaaS transport

- **REQ-AI-001:** Use the canonical SoCLaaS OpenAI-compatible base URL
  `https://soclaas-api.comp.nus.edu.sg/v1` and server-side Bearer authentication.
  An optional trailing slash is normalized. Reject user information, a non-default
  port, query, fragment, different origin, or different path. Redirects are
  disabled and ordinary TLS certificate and hostname verification is mandatory.
- **REQ-AI-002:** Text generation uses non-streaming POST /chat/completions. It
  sends a fixed system message plus one structured user-data message and no tools,
  function definitions, previous messages or persistent response ID.
- **REQ-AI-003:** The configured model comes from `SOCLAAS_MODEL`. Validate it
  against `GET https://soclaas-api.comp.nus.edu.sg/v1/models` as described by
  AI-SEC-020; never hard-code a model name. Resolve `models` and
  `chat/completions` structurally relative to the canonical `/v1/` base so `/v1`
  is never duplicated.
- **REQ-AI-004:** Configuration uses `SOCLAAS_BASE_URL`, `SOCLAAS_API_KEY`,
  `SOCLAAS_MODEL`, `AI_ENABLED`, `AI_TIMEOUT_SECONDS`,
  `AI_MAX_OUTPUT_TOKENS`, `AI_MAX_IN_FLIGHT`, `AI_RATE_PER_MINUTE` and
  `AI_RATE_PER_HOUR`. `AI_ENABLED` defaults to `false`. When enabled, the key and
  model are required, non-blank and non-placeholder, and the base URL must
  canonicalize exactly as REQ-AI-001 requires. Numeric values are positive
  integers and may preserve or tighten, but never exceed, 30 seconds, 800 output
  tokens, five in-flight calls, five Account calls per rolling minute and 30 per
  rolling hour. Missing, placeholder, malformed or security-weakening
  configuration marks AI unavailable without loading protected context or
  blocking core application startup. Secrets have no committed default. Only a
  validated enabled `AiConfiguration.Available` may construct a provider core.
  Disabled or unavailable configuration constructs no core, schedules no
  executor/timer work and makes zero internal-provider-port calls. Configuration
  is immutable for the process lifetime in Iteration 2A; runtime replacement is
  out of scope.
- **REQ-AI-005:** The external interfaces are the feature-specific
  `SmartReportAssistant`, `WorkPlanAssistant` and `TriageAssistant`. They return
  either the feature's validated typed suggestion or a safe categorized failure.
  Current Account and correlation ID are server-derived. Callers provide only
  the feature's permitted user input or object reference and cannot select a
  feature identity, actor ID, correlation ID, template, parser, model, policy or
  arbitrary context map. An internal shared coordinator receives a mandatory
  persisted eligibility probe, lazy allowlisted context loader, freshness
  snapshot and post-call eligibility recheck from the role module. Callers never
  handle bearer headers, raw provider JSON, retry logic or usage extraction.
  Behind that coordinator, an internal provider port exposes only `listModels`
  and `complete`. The provider core exposes a non-blocking readiness-start
  operation and permits completion only after the configured model is in the
  `READY` state. Actual Spring lifecycle/startup invocation is deferred to a
  later configuration-bean increment and must occur only for
  `AiConfiguration.Available`. Raw transport bodies and provider envelope details
  never cross the provider-core seam. In Iteration 2A, `SocLlmProviderCore`,
  `SocLlmProviderPort`, provider decoder/body/failure types, their constructors
  and every mutable input are package-private implementation details. Role and
  web packages must not import `ai.provider`. The later shared coordinator or
  factory will be implemented inside the provider package and expose only the
  three feature-specific assistant APIs; it does not exist in Iteration 2A.
- **REQ-AI-006:** The production adapter uses HTTPS SoCLaaS; automated tests use
  a local fake adapter capable of success, invalid JSON, delayed response, 400,
  401, 403, 429, 503, connection failure, invalid or oversized envelopes,
  cancellation, usage present/absent and model-discovery outcomes. The
  deterministic fake completes caller-controlled pending futures and uses no
  sleeping or wall-clock delay. Fake raw bodies pass through the same bounded
  UTF-8 decoder and envelope validation used for production responses.

### Iteration 2B HTTPS adapter

Iteration 2B adds a package-private `SocLlmHttpsProviderAdapter` behind the
package-private `SocLlmProviderPort`, plus a package-private HTTP transport seam
whose production implementation uses JDK `HttpClient`. Adapter, transport,
production transport, constructors, response-body wrappers and credential
plumbing remain inaccessible to role and web packages. The JDK client uses
`HttpClient.Redirect.NEVER` and the JDK's default TLS trust and hostname
verification; no permissive trust manager, hostname verifier or redirect
following is permitted.

REQ-AI-001 endpoint construction is structural from the already validated
canonical base. Model discovery is exactly
`https://soclaas-api.comp.nus.edu.sg/v1/models`; completion is exactly
`https://soclaas-api.comp.nus.edu.sg/v1/chat/completions`. Construction must not
duplicate `/v1`, change authority, add a query or fragment, or accept a response
whose effective URI differs from the request URI.

The adapter applies exactly these application-controlled request values:

- model discovery: `GET`, no request body, `Accept: application/json`,
  `Accept-Encoding: identity`, and `Authorization: Bearer <server credential>`;
- completion: `POST`, UTF-8 JSON, the same three headers, plus
  `Content-Type: application/json`;
- the completion JSON root contains exactly `model`, `messages`, `max_tokens`
  and `stream`; `messages` contains exactly one `system` message followed by one
  `user` message, each with only `role` and `content`; `max_tokens` is the
  validated request value and `stream` is `false`. No tools, functions, prior
  messages, response identifier or unlisted request member is sent. JSON string
  escaping is deterministic, and malformed surrogate input fails before send.

The validated `AiConfiguration.Available` supplies bearer authorization through
a credential-applying capability. The adapter can apply that capability to an
HTTP request builder but cannot obtain the raw key as a returned `String` or
include it in `toString`, exception messages, results, logs or test diagnostics.
No API outside the configuration/provider boundary exposes that capability.

Model discovery receives the configured absolute expiry. Completion receives the
earlier of its absolute caller deadline and a newly computed configured-timeout
ceiling; that same remaining duration is installed on its `HttpRequest`. An
already expired deadline makes zero transport calls. Caller cancellation requests
cancellation of the unique transport stage; timeout/cancellation and provider-core
detachment ignore late completion and close any late response body. Transport
cancellation and deadline signals contain no request body, response body or
credential diagnostics.

Only HTTP 200 proceeds to decoding. `Content-Encoding` may be absent or contain
only `identity` tokens; every other or malformed encoding fails closed as
`UNSUPPORTED_CONTENT_ENCODING`. The identity response stream is handed directly
to the existing bounded post-decompression `DecompressedBody` decoder, which
retains the 262,145-byte abort and all REQ-AI-005/AI-SEC-010 bounds. Ownership is
explicit: the decoder owns and closes a handed-off HTTP 200 identity body; the
adapter closes every body that is rejected or never handed off, including
non-200, redirect/effective-URI rejection, cancellation, timeout, late completion
and exceptional paths. Null response/body and read/close failures map only to
existing safe provider failures and never disclose diagnostics.

Iteration 2B status mapping is closed and exact: 400 -> `HTTP_BAD_REQUEST`; 401
-> `HTTP_UNAUTHORIZED`; 403 -> `HTTP_FORBIDDEN`; 429 ->
`HTTP_RATE_LIMITED`; 503 -> `HTTP_SERVICE_UNAVAILABLE`; any 3xx status or changed
effective response URI -> `REDIRECT_REJECTED`; every other non-200 ->
`HTTP_UNEXPECTED`; deadline/HTTP-client timeout -> `TIMEOUT`; caller/transport
cancellation -> `CANCELLED`; and connection, DNS, TLS or other transport failure
-> `NETWORK_FAILURE`. Request construction/encoding failure is
`REQUEST_BUILD_FAILED`. These failures carry only their closed code and permitted
retry metadata, never provider bodies, headers, causes or attacker values.

For a 429 response only, retry advice is retained when there is exactly one
`Retry-After` field value and it is either decimal delta-seconds from 0 through 2
inclusive or an RFC-1123 date resolving, against the injected clock, to a
non-negative duration no greater than two seconds. Multiple, blank, negative,
overflowing, malformed or out-of-range values produce no retry advice. Parsing
the header does not itself retry or reset a deadline.

Iteration 2B explicitly excludes retry execution, Account/global rate and lease
enforcement, telemetry persistence, Spring construction/lifecycle wiring, the
shared role-feature gateway/coordinator, and compressed-response decompression or
a compressed-wire byte limit. Those remain later increments; Iteration 2B sends
and accepts identity encoding only.

### Iteration 2C provider lifecycle wiring

Iteration 2C wires the validated configuration, HTTPS adapter and provider core
into the Spring application without making readiness a startup blocker. Only an
`AiConfiguration.Available` value may construct the provider runtime. Disabled
and unavailable configuration receives an inert lifecycle view that creates no
provider core, transport, executor, scheduler or provider call; its terminal
state is respectively `DISABLED` or `UNAVAILABLE` and close is idempotent.

An available runtime owns separate daemon discovery and provider-dispatch
executors plus a scheduler. It starts the existing non-blocking model-readiness
check after bean construction, maps provider readiness to the narrow lifecycle
view, and closes the core and owned resources idempotently during Spring bean
destruction. Discovery work must use the discovery executor; chat work must use
the provider-dispatch executor. Readiness timeout, negative caching, generation
fencing and late-callback handling remain the provider-core rules from
Iteration 2A. The runtime factory has a package-private deterministic seam for
tests; no role or web package may depend on provider implementation types.

### Iteration 2D Technician Work Plan vertical slice

Iteration 2D connects the startup-safe provider runtime to the first role-facing
feature without exposing provider implementation types. `WorkPlanAssistant` is
the public typed seam; its result is either a bounded read-only `WorkPlanResult`
or a stable `WorkPlanFailureCode`. `AiProviderLifecycle` and the assistant are
registered as separate Spring seams so lifecycle injection remains unambiguous
for disabled, unavailable and available configurations.

`POST /technician/requests/{displayId}/work-plan` is the manual HTTP entry point.
The service checks the authenticated Technician role before loading protected
context, loads only an assigned `ASSIGNED` or `IN_PROGRESS` Request and its
latest ten Requester Updates, and sends an allowlisted projection. Request and
Account identifiers, Work Logs, playbooks, credentials and unrelated records
are not serialized into the model prompt. The provider response is parsed with
exact fields, strict duplicate/unknown-field rejection, size/control/markup
limits and mandatory safety/evidence/stop sections. Unsafe or malformed output
is discarded as `AI_INVALID_OUTPUT`.

After a successful model response, the service rechecks the assignment, active
status and Request version. A changed eligibility snapshot discards the entire
result as `DISCARDED_ACCESS_CHANGED`; no business record is written. Provider
failure, readiness failure and repository failure map to safe typed failures and
leave the deterministic Technician workflow available. The endpoint remains
subject to the existing authenticated `/technician/**` security rules and POST
CSRF protection.

This vertical slice deliberately leaves provider retry execution, Account/global
lease enforcement, durable AI telemetry and compressed-response support for
subsequent increments.

### Iteration 2E shared admission coordinator

Iteration 2E introduces `AiInvocationCoordinator`, a provider-neutral Spring
component shared by role services. It admits a logical invocation only when the
configured process-wide in-flight limit and the caller Account's one-minute and
one-hour rolling windows both have capacity. Admission occurs after readiness
has confirmed a usable provider but before protected Work Plan context loading,
so a limit denial performs no repository or provider work and maps to the
stable `AI_LIMITED` result. An idempotent permit is released on every load,
exception and asynchronous completion path, including post-call freshness
discard. The coordinator uses an injected clock in tests and does not expose
provider types or secrets.

This is a single-process control only. PostgreSQL-time buckets, fenced global
leases, durable STARTED/terminal telemetry, retry accounting and multi-feature
coordination remain deferred to the distributed coordination increment.

### Iteration 2F durable distributed admission

Iteration 2F adds `JdbcAiAdmissionBackend` behind the existing coordinator seam.
Account timestamps are pruned and counted using PostgreSQL `CURRENT_TIMESTAMP`
inside a row-locking transaction, so application clocks cannot diverge across
instances. A successful admission claims one of five global slots with a random
owner UUID and monotonically increasing version fence; the 45-second lease is
renewed every 15 seconds. Release and renewal require the exact slot, owner and
version, so a stale request cannot release or renew a replacement lease. No
slot or database failure fails closed before protected context/provider work.

The Technician service now admits before readiness, allowing the durable lease
to span readiness, context loading, provider work and the freshness recheck.
Lease loss causes a completed result to be withheld as `AI_UNAVAILABLE`.
Process-local admission remains the deterministic test fallback when no durable
adapter is supplied.

Iteration 2F does not yet persist `STARTED`/terminal invocation telemetry or
execute provider retries; those are the next lifecycle increment.

### Provider-core readiness

Readiness has exactly four states: `UNVALIDATED`, `CHECKING`, `NEGATIVE` and
`READY`. Each readiness attempt has an immutable generation/identity. Only the
current generation that still owns `CHECKING` may publish a state transition.
Timeout or provider-core closure invalidates that ownership; every late callback
is ignored. Closure adds no public readiness state. The provider core uses a
separately injected `Executor` for discovery dispatch and deterministic
`ScheduledExecutorService`, or a narrower equivalent scheduler abstraction, for
timeout/deadline events. Executor saturation therefore cannot suppress timeout.
Tests inject both dependencies and do not depend on wall-clock sleeps.

The provider-dispatch `Executor` runs both `listModels` and `complete` port
method invocations. Neither a public/core entry point nor its caller assumes
that a port method is non-blocking. Readiness and chat use a two-phase
reserve/invoke/install protocol: reserve ownership under the applicable monitor,
release it, invoke the port on the provider-dispatch executor, then install or
attach the returned provider stage only if ownership is still current. No port
method, provider-stage attachment or cancellation, internal future completion,
or caller/dependent callback executes while a readiness or call monitor is held.
Deadline, cancellation and core closure can therefore publish a terminal result
while a port invocation is synchronously blocked. If a stage arrives after its
generation or call has lost ownership, cancel it best-effort outside the monitor
and ignore it. Executor rejection is a safe failure with zero port work. Every
internal future is completed only after releasing the applicable monitor.

`CHECKING` owns one shared future and one absolute expiry fixed when the check
starts. Joiners never reset it. Concurrent readiness-start or first-use requests
join that generation rather than issuing duplicate `listModels` calls. Caller
cancellation or timeout detaches only that caller and must not cancel or mutate
the shared check. Readiness-start exposes only a read-only completion-stage view;
callers cannot obtain or mutate the shared future, and caller detachment cannot
complete, replay or otherwise alter shared readiness. The core must successfully
schedule that generation's shared
absolute-expiry timeout before submitting discovery work to the executor or
invoking `listModels`. Shared-timeout scheduling rejection transitions the owned
generation directly to `NEGATIVE` and performs zero discovery-executor and port
work. If later discovery-executor submission rejects, cancel the installed timer
and transition the still-owned generation to `NEGATIVE`.

Every check uses the configured timeout. Timeout, malformed discovery data,
missing configured model, provider/transport failure, discovery-executor
rejection or shared-timeout scheduling rejection may transition the current
owned `CHECKING` generation to `NEGATIVE`. The 60-second negative-cache interval
starts at the accepted terminal transition, not at attempt start or late callback.
While it is active, completion is unavailable and no discovery call starts. At
or after expiry, next use starts or joins one new generation. Exact configured-
model discovery may transition the current generation to `READY`, cached for the
process lifetime.

Public completion receives an absolute caller deadline. A caller joins readiness
only until the earlier of that deadline or the shared check expiry. After
`READY`, the core rechecks that deadline and must successfully install per-call
deadline/cancellation enforcement before invoking port `complete`. An expired
deadline, prior cancellation or scheduling rejection returns a safe caller
failure and makes zero chat port calls. `ProviderChatRequest` carries the same
absolute deadline for later adapter enforcement.

Core completion returns a narrow, read-only cancellable call handle. The handle
exposes only a read-only completion-stage/result view and an explicit `cancel`
operation. It exposes no internal `CompletableFuture` and no externally reachable
`complete`, `completeExceptionally`, `obtrude`, timeout-mutation or replay path;
external actions cannot alter internal call state or manufacture a result.

Each chat call has one unique, non-shared provider stage. After dispatch, caller
cancellation or deadline atomically competes with provider completion for one
terminal outcome. If cancellation/deadline wins, it detaches and terminates that
caller, makes a best-effort cancellation request to the unique provider stage,
ignores every late provider/decode completion and returns no content. If safe
provider completion wins first, its result is returned and later cancellation
has no effect. The port stage exposes cancellation without including request or
response content in diagnostics. No chat cancellation, timeout, transport or
decode result demotes `READY`.

One configured deadline starts at AI use-case entry after web authentication and
CSRF checks. It covers eligibility, admission, context collection, readiness,
all waits and provider attempts, envelope decoding, validation, freshness
recheck, terminal telemetry and lease release; it never resets. One automatic
retry is the maximum. Network failure or HTTP 503 waits 250 milliseconds before
retry. HTTP 429 is retried only when a `Retry-After` delta-seconds value or
RFC-1123 date resolves to 0–2 seconds inclusive and at least five seconds remain
under the original deadline. All other existing no-retry rules remain in force.

### Bounded provider decoding

The decoder receives a streaming post-decompression body, reads at most 262,145
bytes and aborts as soon as byte 262,145 is observed, before full-body
materialization or JSON parsing. It never parses a truncated prefix. Decoded
bytes must be strict UTF-8. Parsing is streaming/bounded before any unrestricted
tree is built and rejects duplicate keys, trailing content and a root-level
error member. Limits are maximum nesting depth 64, JSON string length 262,144,
numeric token length 20, total JSON tokens 8,192 and model entries 1,000. A
compressed-wire byte limit remains deferred to a later compressed-transport
increment.

Envelope fields use location allowlists; every unlisted member rejects on
presence. Permitted inert metadata may have any JSON type but is never consumed:

- models root: only `object`, `data`;
- each model entry: only `id`, `object`, `created`, `owned_by`;
- chat root: only `id`, `object`, `created`, `model`, `system_fingerprint`,
  `service_tier`, `choices`, `usage`;
- choice: only `index`, `message`, `finish_reason`, `logprobs`;
- message: only `role`, `content`;
- usage: only `prompt_tokens`, `completion_tokens`, `total_tokens`.

`listModels` requires a root object with a non-null `data` array. Every array
entry is a non-null object containing a non-empty string `id`; IDs must be unique.
Any malformed/null entry, empty/non-string ID, duplicate ID or 1,001st entry
rejects the entire envelope. Readiness succeeds only on an exact configured-
model ID match.

A completion response contains exactly one assistant choice. `message.role` is
exactly `assistant`, `message.content` is one non-empty string and
`finish_reason` is exactly `stop`. Because the message allowlist contains only
role/content, `refusal`, `audio`, `name`, `tool_calls`, `function_call` and every
other unknown member reject on presence even when null or empty. Reject root
error members, empty/multiple choices, non-assistant messages,
missing/malformed content and truncated completion.

`usage` may be absent. When present it is non-null, non-empty and contains only a
non-empty subset of the standard `prompt_tokens`, `completion_tokens` and
`total_tokens` fields. Partial standard usage is allowed. Each supplied value is
a non-negative integral signed-64-bit value; arbitrary aliases and fractional,
overflowing or negative values reject the envelope. When all three standard
fields are present, total must equal prompt plus completion without overflow.

Iteration 2A defines the provider-neutral port, readiness and decoder behavior.
Iteration 2B defines the production HTTPS adapter and closed transport/status
mapping above; retry execution and compressed-response handling remain deferred.

Decoder failures expose only closed, stable error codes. Exceptions, return
values and logs exclude raw bodies, model/provider responses, provider error
content, attacker-controlled values, parser messages, source locations, excerpts,
arbitrary causes and object `toString()` output. Parser source-in-location is
disabled where supported. No cause chain crossing the provider-core seam may
retain sensitive provider/parser content.

A checked or unchecked streaming-body read failure maps to the stable
`BODY_UNREADABLE` code without retaining or exposing its cause or message;
canary-bearing unchecked failures receive the same treatment. An already-safe
`ProviderFailure` code is preserved rather than wrapped with unsafe diagnostic
data. Checked and unchecked body-close failures are suppressed on every path:
they neither replace the primary result/failure nor enter a cause chain, return
value or log.

## Prompt contract

Each prompt contains:

1. feature purpose and explicit advisory/no-action boundary;
2. exact JSON output shape and limits;
3. instruction to treat all delimited context as untrusted data;
4. the minimum allowlisted context with provenance labels;
5. instruction to avoid facts not supported by context and to ask questions
   where information is missing.

Prompt templates are immutable per request and carry a source-controlled version.
Input is JSON-serialized by the application; string concatenation shall not
construct executable instructions. Before serialization, normalize CRLF and CR
to LF, normalize Unicode to NFC, and apply only the field's existing outer-trim
rule. Preserve permitted internal whitespace. The 12,000-code-point aggregate
limit counts the final system-message content plus the final JSON-serialized
user-message content, including JSON syntax, escaping, provenance labels and
delimiters. A count of 12,001 is rejected locally; input is never silently
truncated.

## Typed results

All objects reject unknown fields. Optional scalar fields are either absent/null
or valid; empty strings are invalid. Strings are plain text, outer-trimmed and
limited by Unicode code points. Reject disallowed Unicode control characters;
LF and TAB are permitted only where the field's rules permit them. Reject HTML
tag syntax, script/style content, Markdown links/images and URI-bearing markup.
Framework output escaping remains mandatory for every accepted string.

### Smart Report result

| Field | Type and limit |
| --- | --- |
| draftTitle | optional string, 5–100 |
| draftDescription | optional string, 10–2,000 |
| draftLocation | optional string, 2–120 |
| suggestedCategory | optional current Category Catalogue value |
| suggestedUrgency | optional Low/Normal/High/Emergency |
| missingInformationQuestions | array 0–5, each 1–200 |
| rationale | string 1–500 |

At least one draft/suggestion field or one missing-information question is
required. Suggested values outside current catalogues are invalid.

### Work Plan result

| Field | Type and limit |
| --- | --- |
| diagnosticSteps | array 1–8, each 1–300 |
| safetyPrecautions | array 1–8, each 1–300 |
| suggestedTools | array 0–10, each 1–120 |
| requesterQuestions | array 0–5, each 1–200 |
| evidenceChecklist | array 1–8, each 1–200 |
| stopOrEscalateWhen | array 1–5, each 1–300 |
| rationale | string 1–500 |

Lists preserve model order but the UI labels them suggestions. No item may contain
active markup.

### Triage result

| Field | Type and limit |
| --- | --- |
| suggestedCategory | current Category Catalogue value |
| suggestedPriority | Low/Medium/High/Critical |
| clarificationQuestions | array 0–5, each 1–200 |
| requiredSkillTags | array 0–8 of unique active Skill Catalogue values |
| optionalSkillTags | array 0–8 of unique active values not in required tags |
| rationale | string 1–500 |

The LLM result contains no Technician identity. Candidate calculation occurs
locally under REQ-MGR-023.

## Feature context allowlists

| Feature | Model receives |
| --- | --- |
| Smart Report | current/rough title, description and location; selected category and reported urgency when present; active category labels; fixed field rules |
| Work Plan | Request title, description, location, category, reported urgency, Manager priority and status; at most ten selected visible Requester Updates; no Work Logs or playbook excerpt in the initial revision |
| Triage | Request title, description, location, current category, reported urgency, Manager priority and status; at most ten selected Requester Updates; active category and active skill labels; no identities or workloads |

For Work Plan and Triage, select the latest ten eligible Requester Updates by
`created_at` descending then immutable ID descending, then send that selected set
oldest-to-newest. Active category and skill labels are sent in
case-insensitive-label order with immutable ID as the internal tie-breaker; the
IDs themselves are not sent. The selection rule is the only permitted
truncation. Any remaining field or aggregate overflow rejects the invocation.

The model never receives usernames, Account IDs, internal IDs, display IDs,
workload tables, assignee candidates, Work Logs, Audit Events, session data,
secrets or unrelated records.

The initial Work Plan sends no playbook excerpt. A later revision may add only
source-controlled, versioned and human-reviewed excerpts after the role context
contract and security tests are reviewed. No user upload, web retrieval or
model-selected retrieval is in scope.

## Failure mapping

| Condition | Gateway result | User behavior |
| --- | --- | --- |
| feature disabled/missing config/model unavailable | AI_UNAVAILABLE | explain manual path |
| local input/rate/concurrency limit | AI_LIMITED | no provider call; retry timing if safe |
| 400 or schema-invalid provider request | AI_INVALID_REQUEST | no retry |
| 401/403 | AI_CONFIGURATION_ERROR | no retry; generic user message and safe operator log |
| 429 | AI_LIMITED | one retry only for approved 0–2-second Retry-After with at least five seconds remaining |
| network/503 | AI_UNAVAILABLE | at most one retry after 250 milliseconds within original deadline |
| deadline/cancel | AI_TIMEOUT | cancel/ignore late response |
| malformed/schema/unsafe output | AI_INVALID_OUTPUT | discard entire result |
| post-call Account/role/object/state/freshness loss | AI_UNAVAILABLE; terminal disposition `DISCARDED_ACCESS_CHANGED` | discard entire result |
| limiter database or global-lease loss | AI_UNAVAILABLE | no provider call after failure; discard any late result |
| terminal telemetry persistence failure after provider use | AI_UNAVAILABLE | withhold result; retain `STARTED` evidence and emit sanitized operator alert |

No partial result is displayed. Provider error bodies are not exposed to users.
The manual form or role operation remains available.

## Usage and audit metadata

- **REQ-AI-007:** Capture provider input, output and total token counts when
  returned, plus feature/model/template version/latency/status/retry count.
  One admitted logical invocation consumes one Account-rate timestamp; its retry
  does not consume another timestamp but increments retry/provider-attempt
  telemetry.
- **REQ-AI-008:** Store no raw prompt or raw output. Safe metadata is retained for
  project life and visible to Managers only as aggregate operational counts.
- **REQ-AI-009:** Compare application totals periodically with SoCLaaS portal
  usage summaries. Differences are recorded as operational evidence, not silently
  rewritten.
- **REQ-AI-010:** AI invocation metadata is not a business Audit Event and cannot
  prove that a suggestion was accepted. Actual business writes keep ordinary
  Audit Events.

After successful admission and before protected context loading or provider use,
create a durable `STARTED` lifecycle record. If that insert fails, do not load
protected context or call the provider. Update it to exactly one terminal
disposition with latency, retry count and usage when supplied. Required terminal
dispositions include the existing safe failure categories, `SUCCEEDED`, and
`DISCARDED_ACCESS_CHANGED`; a post-call access/freshness discard is never
recorded as `SUCCEEDED`. If terminal persistence fails after provider use, withhold
the result, return `AI_UNAVAILABLE`, leave the `STARTED` evidence intact and emit
only a sanitized operational alert. The necessary status/check/grant schema
revision requires separate review before implementation.

Authentication, CSRF, early size, disabled/configuration and pre-admission local
failures do not consume Account quota. An admitted logical invocation consumes
one timestamp regardless of retry. A fenced global lease spans context loading,
readiness, provider/retry, validation, freshness recheck and terminal telemetry.
It lasts 45 seconds and renews every 15 seconds using database time and an
owner/version fencing token. Lease loss discards output, and release succeeds
only for the matching fencing token. Limiter database failure fails closed and
makes no provider call. Any lease-owner/fencing storage revision requires review
before implementation.

## Integration acceptance

Contract tests shall cover every schema minimum/maximum, unknown and duplicate
fields, trailing content, invalid enums/tags, control/markup rejection, escaping,
12,000/12,001-code-point inputs, all failure mappings, retry timing/ceiling,
cancellation, 256 KiB response bounds, every provider-envelope rule, token usage
present/absent/inconsistent, no raw-content logging, readiness caching and
single-flight recovery, exact role context allowlists/update ordering, quota and
fenced-lease behavior, telemetry failures, freshness loss and zero context loads,
readiness calls and provider calls on early denied authorization.

Provider-core contract tests additionally cover every readiness transition,
generation ownership, timeout-versus-late-callback races, closure, single-flight
joining, immutable shared expiry, configured timeout, negative-cache start and
boundaries, separate discovery/scheduler rejection, caller-deadline scheduling
rejection, cancellation isolation and process-lifetime success caching using an
injected clock, executor and scheduler. Disabled/unavailable configuration tests
assert no provider core, executor/timer work or port call. Tests prove caller
timeout/cancellation never cancels a shared check and never permits a late chat
call, absolute deadlines reach `ProviderChatRequest`, and chat/decode failure
does not demote `READY`. No-sleep readiness tests assert timeout scheduling
precedes discovery submission/`listModels`, scheduling rejection performs zero
executor/port work, and later discovery-submission rejection cancels the timer.
No-sleep chat race tests cover cancellation/deadline before dispatch, both after
dispatch, provider-response wins, cancellation wins, best-effort unique-stage
cancellation and every late provider/decode completion being ignored.

Additional deterministic no-sleep tests block a synchronous `listModels`
invocation and prove timeout and core closure complete without releasing it;
block synchronous `complete` and prove cancellation/deadline returns promptly,
then cancel and ignore any late stage. Tests prove port invocation, stage
attachment/cancellation, internal completion and dependent callbacks never run
under readiness/call monitors, and executor rejection performs zero port work.
API tests prove readiness and call results expose no external mutation, timeout
mutation or replay path. Visibility/architecture tests prove all Iteration 2A
provider implementation types and mutating inputs are package-private and no
role/web package depends on `ai.provider`.

Model/completion decoder tests use fake streaming raw bodies and cover strict
UTF-8, abort at byte 262,145 before materialization, depth/string/number/token and
1,000/1,001-model limits, duplicate/trailing/root-error input, every location
allowlist and unlisted-field rejection, malformed/null/duplicate model entries,
exact model matching, every choice/content/finish rule and all permitted/invalid
usage shapes. Full cause-chain and captured-log canaries prove raw, attacker and
parser content cannot escape stable error codes. Pending futures provide
deterministic delay/cancellation coverage without sleeps. Read and close-failure
tests throw checked and unchecked canary-bearing failures and prove the result is
`BODY_UNREADABLE` where applicable, safe `ProviderFailure` codes are preserved,
and no canary appears in a result, cause chain or captured log.

Iteration 2B adapter tests shall use the package-private transport seam and an
injected clock, with no external network or sleeps. They cover exact methods,
canonical URIs, application-controlled headers and byte-equivalent JSON shape;
credential application without raw-key access or diagnostic leakage; JDK redirect
policy/default TLS construction; identity/unsupported content encodings; bounded
decoder handoff; body closure on every terminal/race path; expired deadlines and
cancellation before/after transport-stage attachment; ignored late responses;
every closed status/timeout/network mapping; and all single/multiple/malformed
`Retry-After` boundaries. Architecture tests prove adapter/transport types and
constructors remain package-private and no role/web package imports them.
