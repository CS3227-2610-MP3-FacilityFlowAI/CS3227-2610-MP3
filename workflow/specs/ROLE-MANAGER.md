# Facilities Manager Specification

Status: Triage Assistant contract drafted 10 October 2026; teammate approval and
remaining Manager workflow details are pending.

Planning coordination: `yu-sutong`. This focus is not exclusive ownership.

## Role description

Facilities Managers validate reports, set operational priority, assign work,
review completion, manage eligible corrections, and audit workflow events.
Detailed MP2 reuse and web-specific behavior must be confirmed before
implementation.

## Permissions

Only an authenticated Facilities Manager may request triage assistance. The
service loads an authorized request and an eligible technician list. The model
never receives account credentials or a mutation tool. Ordinary assignment and
priority endpoints repeat role, state, technician eligibility, CSRF, and record
version checks.

## Normal workflow

The core Manager workflow remains fully usable without AI. The planned MP2
baseline assigns an `OPEN` request to an active Technician with a Manager
priority of `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL`.

## Triage Assistant

### Requirement REQ-MGR-001

For an `OPEN` request, an authenticated Facilities Manager may ask the SoC LLM
for a structured triage suggestion containing:

- `suggestedCategory`
- `suggestedPriority`
- `recommendedTechnicianId`
- `reasons`
- `clarificationRequired`
- `clarifyingQuestion`
- `confidence`

The server supplies the category and priority allowlists plus eligible
technician identifiers, approved skill tags, and current active-work counts.
The gateway rejects a recommended identifier or enum outside the supplied
values. The model does not receive unrelated request histories or sensitive
account fields.

The Manager may copy or edit the suggestion, then uses the ordinary assignment
workflow. Generating or accepting a suggestion does not set priority, assign a
Technician, or alter request state.

### Acceptance criteria

1. Given an authenticated Manager and a current `OPEN` request, requesting
   triage returns either a schema-valid suggestion or a safe error without
   changing the request.
2. Category, priority, technician identifier, and confidence must satisfy the
   server-supplied schema and allowlists. Any invalid value rejects the complete
   AI result.
3. The interface displays reasons and uncertainty and requires a separate
   assignment action.
4. The assignment action repeats authorization, state, active-technician,
   priority, CSRF, and record-version checks; a stale suggestion cannot bypass
   them.
5. A timeout, rate limit, malformed response, or unavailable SoC LLM leaves
   manual triage and assignment available.

## Security and test scenarios

- Prompt injection in a report cannot reveal another report, expand context, or
  cause assignment.
- The model cannot return an inactive or omitted Technician identifier.
- Workload and skill context is limited to the fields required for the
  recommendation.
- Concurrent assignment or status changes invalidate the stale action.
- Tests confirm that generation and failure do not set priority, assign,
  transition, or correct a request.

Shared controls: `REQ-AI-001`, `REQ-AI-002`, and `AI-SEC-001` through
`AI-SEC-005` in the AI security and integration specifications.

## Unresolved questions

Approve the skill-tag source, active-workload calculation, confidence values,
clarification behavior, exact quotas, timeout, retention, and the policy for
unsafe or low-confidence recommendations.
