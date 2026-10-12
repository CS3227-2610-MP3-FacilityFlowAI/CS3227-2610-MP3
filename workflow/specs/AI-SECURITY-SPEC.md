# AI Security Specification

Status: shared AI controls drafted 10 October 2026; exact limits, retention,
unsafe-output policy, and teammate approval remain pending. No control is
implemented or verified by this document.

## Assets and trust boundaries

Protected assets include account and request data, prompts, approved playbooks,
model outputs, SoC LLM credentials, audit metadata, and rate-limit state. Trust
boundaries exist between browser and service, service and database, context
builder and model, model output and application UI, and development-agent
handoffs.

## AI SEC 001 Authorization before context

The service authenticates the caller and authorizes the role, record,
ownership or current assignment before collecting context or calling the SoC
LLM. It supplies only fields required by the selected role feature. A denied
request produces no LLM call and no protected context leaves the service.

## AI SEC 002 Typed output and allowlists

Each feature has a separate closed schema. Unknown fields, invalid types,
oversized lists, invalid enums, and identifiers outside server-supplied
allowlists reject the complete result. Output is escaped when displayed. Model
text is never treated as authorization, HTML, a database command, or an agent
instruction.

## AI SEC 003 Injection resistance and context provenance

User text, stored reports, follow-ups, work logs, and retrieved playbooks are
untrusted data. Prompt templates delimit instructions from data and state that
data cannot alter the task or reveal hidden instructions. The Technician
feature uses only approved playbooks with provenance; the MVP does not browse
the web or retrieve arbitrary documents. Tests cover direct, indirect,
obfuscated, encoded, Unicode, and cross-role exfiltration attempts.

## AI SEC 004 Limits and failure containment

The gateway enforces authenticated per-user and global limits, bounded input and
output sizes, concurrency limits, a timeout, and bounded retries that respect
SoC LLM 429 responses. Exact values require approval. Timeout, partial output,
malformed output, exhaustion, and unavailability return a safe error and do not
change application records. The normal workflow remains available.

## AI SEC 005 Approval and audit boundary

AI endpoints have no mutation tools and do not call ordinary write services.
Users review suggestions, then use separate endpoints that repeat role,
ownership or assignment, state, CSRF, validation, eligible-value, and record
version checks. Safe audit metadata records the authenticated actor, feature,
template and schema versions, target record identifier where applicable,
outcome, latency, and error class. Credentials and raw authorization headers
are never logged. Raw prompt and response retention requires a separate approved
policy.

## Required security tests

- Direct, indirect, obfuscated, encoded, and Unicode prompt injection for every
  feature.
- Requester-to-Requester, Technician-to-Technician, and cross-role isolation.
- Invalid enums, unknown fields, inactive or omitted technician identifiers,
  malformed JSON, HTML/script-like output, and oversized input/output.
- Direct HTTP attempts to bypass the human approval step or call a mutation with
  stale state, stale version, missing CSRF, changed role, or changed assignment.
- SoC LLM timeout, HTTP 429, partial response, unavailable service, concurrent
  requests, and limit exhaustion.
- Log and error inspection for credentials, raw authorization headers,
  unrelated records, and unnecessary personal data.

Each test records the expected safe outcome, actual result, evidence, fix where
needed, and fresh verification. Tests must also prove that AI generation and AI
failure produce no business mutation.

## Human oversight

The interface labels output as advisory, presents reasons and uncertainty where
provided, and allows the user to ignore or edit suggestions. A human approves
specifications, security exceptions, non-trivial merges, and production
promotion.

## Unresolved questions

Approve exact quotas, token and list budgets, timeout and retry values, error
codes, raw-content retention, deletion, unsafe-output escalation, playbook
ownership, disclosure channel, and incident response.
