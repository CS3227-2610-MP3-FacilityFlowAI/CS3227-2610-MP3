# Role Specific AI Assistants

- Decision ID: ADR-002
- Date: 2026-10-10
- Status: approved direction; detailed specifications pending teammate review
- Related requirements: `REQ-RQ-001`, `REQ-TECH-001`, `REQ-MGR-001`,
  `REQ-AI-001`, `REQ-AI-002`, `AI-SEC-001` through `AI-SEC-005`

## Context

MP3 requires at least one LLM-based feature for every application role. The
features must be useful within the existing FacilityFlow workflow without
allowing model output to bypass authorization or change records autonomously.

## Decision

- Requesters receive a Smart Report Assistant that drafts structured request
  fields, suggests category and urgency with reasons, identifies missing
  information, and gives limited safety advice.
- Technicians receive a Work Plan Assistant that suggests safety checks,
  diagnostic steps, tools, requester questions, and evidence to collect for an
  assigned request.
- Facilities Managers receive a Triage Assistant that suggests category,
  priority, clarification needs, and one eligible technician with reasons and
  confidence.

All features use the SoC LLM through a shared gateway. They return advisory,
typed results. They cannot submit a request, write a work log, assign a
technician, change priority, transition a request, or close a request.

## Consequences

- Each role needs a separate prompt template, typed output schema, user-review
  interface, authorization check, and normal fallback path.
- The gateway must validate allowlisted values and record safe audit metadata.
- Business mutations remain separate endpoints and repeat authorization,
  ownership, state, CSRF, and version checks.
- Direct, indirect, and obfuscated prompt-injection tests are required for all
  three features.

## Approval evidence

The user selected this feature bundle and instructed Codex to update the
repository on 10 October 2026. Teammate review of the detailed specifications
remains required.
