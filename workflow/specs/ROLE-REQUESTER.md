# Requester Specification

Status: Smart Report Assistant contract drafted 10 October 2026; teammate
approval and remaining Requester workflow details are pending.

Planning coordination: `yooplo`. This focus is not exclusive ownership.

## Role description

Requesters create and manage their own maintenance reports, add follow-up
information, and view the public status history of their reports. Detailed MP2
reuse and web-specific behavior must be confirmed before implementation.

## Permissions

The service authorizes every read and write against the authenticated account.
A Requester cannot read or reference another Requester's report through either
the ordinary workflow or the AI feature. Hiding a control in the interface is
not an authorization control.

## Normal workflow

The core report form remains fully usable without AI. Existing MP2 validation
provides the planned baseline: title 5 to 100 characters, description 10 to
2,000 characters, location 2 to 120 characters, a catalogue category, and a
reported urgency. These constraints require teammate confirmation in the MP3
product specification before implementation.

## Smart Report Assistant

### Requirement REQ-RQ-001

An authenticated Requester may send the current unsaved description to the SoC
LLM and receive a structured draft containing:

- `suggestedTitle`
- `suggestedCategory`
- `suggestedUrgency`
- `urgencyRationale`
- `missingInformation`
- `safetyAdvice`

The server supplies the current category catalogue and urgency allowlist. The
gateway rejects values outside those lists and unknown output fields. The
assistant does not receive other request records or account data that is not
required to prepare the draft.

The result appears beside the editable report form. The Requester may copy or
edit individual suggestions. Generating or accepting a suggestion does not
submit the report or persist business fields. Submission uses the normal
authorized endpoint and repeats validation and CSRF checks.

### Acceptance criteria

1. Given an authenticated Requester and a description within the approved
   length, requesting assistance returns either a schema-valid suggestion or a
   safe error without changing a request record.
2. A suggested category and urgency must belong to the server-supplied
   allowlists. Unknown fields or invalid values cause the complete AI result to
   be rejected.
3. The response shows the rationale, missing-information questions, and any
   safety advice as suggestions rather than verified facts or completed form
   fields.
4. The Requester must use the ordinary submission action before a request is
   created. That action repeats authorization, validation, and CSRF checks.
5. A timeout, rate limit, malformed response, or unavailable SoC LLM leaves the
   entered form content intact and permits manual submission.

## Security and test scenarios

- Direct, obfuscated, and stored prompt-injection text cannot disclose system
  prompts, change the schema, or cause an application mutation.
- A Requester cannot add another account or request identifier to obtain its
  contents.
- HTML and script-like model output is rendered as text or rejected.
- Oversized input is rejected before an LLM call.
- Tests confirm that AI generation and AI failure create no request, audit
  transition, or hidden state change.

Shared controls: `REQ-AI-001`, `REQ-AI-002`, and `AI-SEC-001` through
`AI-SEC-005` in the AI security and integration specifications.

## Unresolved questions

Confirm the retained MP2 field limits and catalogue source, safety-advice
wording and escalation behavior, exact quotas, timeout, retention, and the UI
for accepting individual suggestions.
