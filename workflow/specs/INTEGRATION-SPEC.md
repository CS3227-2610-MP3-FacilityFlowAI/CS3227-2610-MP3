# Integration Specification

Status: approved baseline v0.1, 12 October 2026. Owner: ngkhengyang. Gateway
revision candidate v0.2 received stakeholder approval on 16 October 2026;
Security Reviewer, affected role-owner, and teammate review of the revision
remains pending. This status is not an implementation or verification claim.
Business eligibility remains in role specifications; this document owns
transport and typed contracts.

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
  blocking core application startup. Secrets have no committed default.
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
- **REQ-AI-006:** The production adapter uses HTTPS SoCLaaS; automated tests use
  a local fake adapter capable of success, invalid JSON, delayed response, 400,
  401, 403, 429, 503, connection failure, invalid or oversized envelopes,
  cancellation, usage present/absent and model-discovery outcomes.

### Readiness, deadline and provider envelope

The application makes a non-blocking model-discovery attempt during startup. A
failed result is negative-cached for 60 seconds. When no result exists or that
negative cache has expired, the first use performs one single-flight readiness
retry while concurrent callers observe the same result. A successful
configured-model validation is cached for the process/configuration lifetime.
Chat is forbidden until the configured model has been validated.

One configured deadline starts at AI use-case entry after web authentication and
CSRF checks. It covers eligibility, admission, context collection, readiness,
all waits and provider attempts, envelope decoding, validation, freshness
recheck, terminal telemetry and lease release; it never resets. One automatic
retry is the maximum. Network failure or HTTP 503 waits 250 milliseconds before
retry. HTTP 429 is retried only when a `Retry-After` delta-seconds value or
RFC-1123 date resolves to 0–2 seconds inclusive and at least five seconds remain
under the original deadline. All other existing no-retry rules remain in force.

The decompressed provider body is limited to 256 KiB. A successful response has
exactly one choice, no tool/function call, one non-empty assistant-message string
and `finish_reason` exactly `stop`. Reject a 200 response containing an error
envelope, empty or multiple choices, missing/malformed content, truncation and
any envelope outside this contract. Usage may be absent. When present, supplied
token counts are non-negative integers and `total_tokens` must equal the sum of
supplied input and output counts when both are present.

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
