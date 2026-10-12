# Role Specific AI Assistants

- Decision ID: ADR-002
- Date: 2026-10-10
- Status: approved; governing specifications approved 12 October 2026
- Related requirements: `REQ-RQ-012` through `REQ-RQ-017`, `REQ-TECH-012`
  through `REQ-TECH-017`, `REQ-MGR-020` through `REQ-MGR-025`, `REQ-AI-001`
  through `REQ-AI-010`, and the `AI-SEC` requirements

## Context

MP3 requires at least one LLM-based feature for every application role. The
features must be useful within the existing FacilityFlow workflow without
allowing model output to bypass authorization or change records autonomously.

## Decision

- Requesters receive a Smart Report Assistant that drafts structured request
  fields, suggests category and urgency with reasons, identifies missing
  information, and leaves every field editable or rejectable.
- Technicians receive a Work Plan Assistant that suggests safety checks,
  diagnostic steps, tools, requester questions, and evidence to collect for an
  assigned request.
- Facilities Managers receive a Triage Assistant that suggests category,
  priority, clarification needs, and required/optional skill tags. The LLM does
  not receive Technician identities or workloads; application code ranks
  eligible Technicians deterministically and the Manager makes the final choice.

All features use the SoC LLM through a shared gateway. They return advisory,
typed results. They cannot submit a request, write a work log, assign a
technician, change priority, transition a request, or close a request.

## Consequences

- Each role needs a separate prompt template, typed output schema, user-review
  interface, authorization check, and normal fallback path.
- The gateway must validate allowlisted values and record safe audit metadata.
- Manager candidate ranking must remain deterministic, testable and separate
  from the LLM response.
- Business mutations remain separate endpoints and repeat authorization,
  ownership, state, CSRF, and version checks.
- Direct, indirect, and obfuscated prompt-injection tests are required for all
  three features.

## Approval evidence

The user selected this feature bundle and instructed Codex to update the
repository on 10 October 2026. Teammate review of the detailed specifications
remains required.
