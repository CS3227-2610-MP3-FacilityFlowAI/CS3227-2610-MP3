# Integration Specification

Status: draft v0.1, 12 October 2026. Owner: ngkhengyang. Required reviewers:
Security Reviewer, one role owner, and one teammate. Business eligibility remains
in role specifications; this document owns transport and typed contracts.

## SoCLaaS transport

- **REQ-AI-001:** Use the SoCLaaS OpenAI-compatible base URL
  https://soclaas-api.comp.nus.edu.sg/v1 and server-side Bearer authentication.
- **REQ-AI-002:** Text generation uses non-streaming POST /chat/completions. It
  sends a fixed system message plus one structured user-data message and no tools,
  function definitions, previous messages or persistent response ID.
- **REQ-AI-003:** The configured model comes from SOCLAAS_MODEL. Validate it
  against GET /models as described by AI-SEC-020; never hard-code a model name.
- **REQ-AI-004:** Configuration uses SOCLAAS_BASE_URL, SOCLAAS_API_KEY,
  SOCLAAS_MODEL, AI_ENABLED, AI_TIMEOUT_SECONDS, AI_MAX_OUTPUT_TOKENS,
  AI_MAX_IN_FLIGHT, AI_RATE_PER_MINUTE and AI_RATE_PER_HOUR. Defaults implement
  AI-SEC-007–009; secrets have no committed default.
- **REQ-AI-005:** The gateway interface accepts feature identity, authenticated
  actor ID, authorized typed context and correlation ID, and returns either a
  validated typed suggestion or a safe categorized failure. Callers never handle
  bearer headers, raw provider JSON, retry logic or usage extraction.
- **REQ-AI-006:** The production adapter uses HTTPS SoCLaaS; automated tests use
  a local fake adapter capable of success, invalid JSON, delayed response, 400,
  401, 403, 429, 503 and connection failure.

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
construct executable instructions.

## Typed results

All objects reject unknown fields. Optional scalar fields are either absent/null
or valid; empty strings are invalid. Strings are plain text, outer-trimmed and
limited by Unicode code points.

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
| Smart Report | rough/current title, description and location; selected category/urgency if present; active category labels; field rules |
| Work Plan | selected assigned Request fields; category, urgency, priority and state; visible Requester Updates; approved relevant playbook excerpts |
| Triage | selected Request fields and triage-relevant Requester Updates; active categories; active skill labels |

The model never receives usernames, Account/display IDs, workload tables,
assignee candidates, Work Logs outside Work Plan authorization, Audit Events,
session data, secrets or unrelated records.

Playbook excerpts must carry a source-controlled identifier and version and be
human-reviewed before use. No user upload, web retrieval or model-selected
retrieval is in scope.

## Failure mapping

| Condition | Gateway result | User behavior |
| --- | --- | --- |
| feature disabled/missing config/model unavailable | AI_UNAVAILABLE | explain manual path |
| local input/rate/concurrency limit | AI_LIMITED | no provider call; retry timing if safe |
| 400 or schema-invalid provider request | AI_INVALID_REQUEST | no retry |
| 401/403 | AI_CONFIGURATION_ERROR | no retry; generic user message and safe operator log |
| 429 | AI_LIMITED | optional single bounded Retry-After retry |
| network/503 | AI_UNAVAILABLE | at most one bounded retry |
| deadline/cancel | AI_TIMEOUT | cancel/ignore late response |
| malformed/schema/unsafe output | AI_INVALID_OUTPUT | discard entire result |

No partial result is displayed. Provider error bodies are not exposed to users.
The manual form or role operation remains available.

## Usage and audit metadata

- **REQ-AI-007:** Capture provider input, output and total token counts when
  returned, plus feature/model/template version/latency/status/retry count.
- **REQ-AI-008:** Store no raw prompt or raw output. Safe metadata is retained for
  project life and visible to Managers only as aggregate operational counts.
- **REQ-AI-009:** Compare application totals periodically with SoCLaaS portal
  usage summaries. Differences are recorded as operational evidence, not silently
  rewritten.
- **REQ-AI-010:** AI invocation metadata is not a business Audit Event and cannot
  prove that a suggestion was accepted. Actual business writes keep ordinary
  Audit Events.

## Integration acceptance

Contract tests shall cover every schema minimum/maximum, unknown fields, invalid
enums/tags, escaping, all failure mappings, retry ceiling, cancellation, token
usage present/absent, no raw-content logging, model discovery failure, role
context allowlists and zero provider calls on denied authorization.
