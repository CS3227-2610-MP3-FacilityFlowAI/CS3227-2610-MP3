# AI Security Specification

Status: approved baseline v0.1, 12 October 2026. Owner: Security Reviewer with
ngkhengyang for gateway controls. Gateway revision candidate v0.2 received
stakeholder approval on 16 October 2026; Security Reviewer, affected role-owner,
teammate and fresh-reviewer review of the revision remains pending. This document
defines required controls and planned tests; it does not claim they are
implemented or verified.

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
- **AI-SEC-006:** Each invocation is stateless. The application sends no previous
  AI conversation and stores no raw prompt or raw output after the response is
  processed.
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
  truncation and inconsistent non-negative usage counts.
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
  workflow available with current user input.
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
  fragment, origin/path changes and redirects; use normal TLS verification.
- **AI-SEC-020:** A configured model is checked against GET /v1/models without
  blocking core application startup. The startup attempt is non-blocking; chat
  is forbidden until validation succeeds. Failure is negative-cached for 60
  seconds. When no result exists or that cache expires, first use allows one
  single-flight retry. Success is cached for the process/configuration lifetime.
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
readiness calls and provider calls.

Execute with synthetic data in an authorized non-production environment and
record actual results using the security-test template. Planned cases are not
passes.
