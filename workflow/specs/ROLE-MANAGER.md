# Facilities Manager Specification

Status: draft scaffold; not approved for implementation. Owner/reviewer: TBD.

Planning context: [PLAN.md](../PLAN.md). Existing planning decisions are preserved;
behavior, acceptance criteria and detailed design still need approval.
Material TBDs block implementation. Approval revision, approvers and evidence: TBD.

## Role description

Planning owner: `yu-sutong`. Detailed responsibilities: TBD.

## Permissions

Allowed operations, ownership scope, data visibility and denied actions: TBD.
Authorization is enforced server-side; UI hiding is insufficient.

## Normal workflows

Preconditions, fields, eligible actions, transitions and outcomes: TBD.

## AI feature

Existing planned feature: Triage Assistant, using the SoC LLM.
Inputs, context scope, typed output and review interaction: TBD. AI may draft or
recommend only; it cannot submit, assign, reprioritize, alter state or close cases.
Users perform business actions through ordinary authorized services.

## Security requirements

Role/ownership isolation, validation, data minimization and audit criteria:
TBD. Link shared threats from AI-SECURITY-SPEC.md.

## Error cases

Invalid input, denied access, stale records, storage failure and AI unavailability,
rate limits or malformed output: responses TBD. Core use must survive AI failure.

## Acceptance criteria

Actor, preconditions, action, observable/forbidden outcomes and failure paths
per requirement: TBD.

## Test scenarios

Normal, boundary, authorization/isolation, injection, approval bypass and AI
failure cases with expected outcomes: TBD.

## Unresolved questions

Eligibility, values, schemas, material behavior and approval evidence: TBD.
