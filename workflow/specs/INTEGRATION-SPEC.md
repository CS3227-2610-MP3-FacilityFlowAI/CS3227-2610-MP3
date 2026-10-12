# Integration Specification

Status: role AI contracts drafted 10 October 2026; SoC LLM endpoint, model,
credentials, exact limits, and teammate approval remain pending.

## Shared gateway

All application AI uses a shared SoC LLM gateway with separate role templates,
input builders, output schemas, validators, limits, and audit events. The model
has no application mutation tools. Credentials load from environment or
deployment secret storage and are never committed or logged.

## Requester contract

- Planned endpoint: `POST /api/ai/request-draft`
- Authorization: authenticated Requester
- Input: current unsaved description plus server-side category and urgency
  allowlists
- Output: `suggestedTitle`, `suggestedCategory`, `suggestedUrgency`,
  `urgencyRationale`, `missingInformation`, `safetyAdvice`
- Mutation: none

## Technician contract

- Planned endpoint: `POST /api/ai/work-plan/{requestId}`
- Authorization: authenticated current assignee; request in `ASSIGNED` or
  `IN_PROGRESS`
- Input: minimum authorized request fields and approved playbook excerpts with
  provenance
- Output: `safetyChecks`, `diagnosticSteps`, `suggestedTools`,
  `requesterQuestions`, `evidenceToCapture`
- Mutation: none

## Facilities Manager contract

- Planned endpoint: `POST /api/ai/triage/{requestId}`
- Authorization: authenticated Facilities Manager; request currently `OPEN`
- Input: minimum request fields, category and priority allowlists, and eligible
  technician identifiers with approved skill tags and active-work counts
- Output: `suggestedCategory`, `suggestedPriority`,
  `recommendedTechnicianId`, `reasons`, `clarificationRequired`,
  `clarifyingQuestion`, `confidence`
- Mutation: none

## Validation and context scope

Authorize before context construction. Each output uses a closed typed schema
and rejects unknown fields. Enum values and identifiers must belong to the
server-supplied set for that request. Rendering escapes model text. The gateway
does not accept caller-supplied account IDs, technician lists, playbook sources,
or mutation instructions as trusted context.

## Limits and failure behavior

Per-user and global budgets, token/input/output limits, concurrency, timeout,
and bounded retries require approval before implementation. Handle HTTP 429,
timeouts, cancellation, partial output, malformed output, and unavailability as
safe errors. Preserve user-entered form content and the normal non-AI workflow.

## Persistence and audit

AI endpoints do not persist business suggestions automatically. Safe audit
metadata follows `AI-SEC-005`. Any later decision to retain prompt or response
content requires an explicit data-classification, retention, deletion, and
access-control decision.

## Environments

Development and production use separate SoC LLM credentials or quotas where
available, separate databases, and separate configuration. Tests use stubs or
approved development access and never call production credentials.

## Integration tests

Contract tests cover schema-valid responses, invalid values, unknown fields,
authorization and isolation, stale state or assignment, rate limits, timeout,
malformed and partial output, concurrent requests, and proof of no mutation.
