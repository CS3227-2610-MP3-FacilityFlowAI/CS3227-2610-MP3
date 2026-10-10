# Technician Specification

Status: Work Plan Assistant contract drafted 10 October 2026; teammate approval
and remaining Technician workflow details are pending.

Planning coordination: `ngkhengyang`. This focus is not exclusive ownership.

## Role description

Technicians view assigned requests, start eligible work, add progress evidence,
and submit completed work for Facilities Manager review. Detailed MP2 reuse and
web-specific behavior must be confirmed before implementation.

## Permissions

The service authorizes the authenticated Technician and verifies the current
assignment before collecting AI context. A Technician cannot request an AI plan
for an unassigned request. Assignment and state are checked again by every
ordinary mutation endpoint.

## Normal workflow

The core Technician workflow remains fully usable without AI. The planned MP2
baseline permits work to start from `ASSIGNED`, progress while `IN_PROGRESS`,
and completion submission from `IN_PROGRESS`. Reassignment or a state change
invalidates stale actions.

## Work Plan Assistant

### Requirement REQ-TECH-001

An authenticated Technician assigned to an `ASSIGNED` or `IN_PROGRESS` request
may ask the SoC LLM for a structured work plan containing:

- `safetyChecks`
- `diagnosticSteps`
- `suggestedTools`
- `requesterQuestions`
- `evidenceToCapture`

The model receives the minimum authorized request fields and, when available,
approved maintenance playbook excerpts with provenance. Stored request text and
playbooks are treated as untrusted data. The MVP does not browse the web or
retrieve arbitrary documents.

The plan is advisory. It cannot start work, add a work log, record evidence,
complete the request, or change any field. The Technician performs those
actions through the normal authorized workflow.

### Acceptance criteria

1. Given the current assignee and an eligible request state, requesting a work
   plan returns either a schema-valid suggestion or a safe error without
   changing the request.
2. An unassigned Technician or an ineligible request is rejected before any LLM
   call and receives no request context.
3. The response separates safety checks, diagnostic steps, tools, questions,
   and evidence. Empty or unsafe mandatory sections cause rejection according
   to the approved output policy.
4. The interface labels the plan as advisory and provides no automatic action
   from a generated step.
5. A timeout, rate limit, malformed response, or unavailable SoC LLM leaves the
   normal Technician workflow usable.

## Security and test scenarios

- Direct and indirect prompt injections inside stored descriptions, follow-ups,
  and approved playbooks cannot override instructions or trigger mutations.
- A Technician cannot substitute an unassigned request identifier.
- Reassignment between authorization and generation is detected before the
  response is shown or used.
- Unsafe instructions and output containing unknown fields are rejected or
  escalated under the approved policy.
- Tests confirm that generation and failure do not start, complete, or update a
  work order.

Shared controls: `REQ-AI-001`, `REQ-AI-002`, and `AI-SEC-001` through
`AI-SEC-005` in the AI security and integration specifications.

## Unresolved questions

Define the approved playbook source and provenance rules, unsafe-output policy,
maximum list sizes, exact quotas, timeout, retention, and whether a resolution
summary draft is included after the MVP.
