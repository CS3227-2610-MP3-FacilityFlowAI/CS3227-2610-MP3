# AI Security Specification

Status: draft scaffold; not approved for implementation. Owner/reviewer: TBD.

Planning context: [PLAN.md](../PLAN.md). Existing planning decisions are preserved;
behavior, acceptance criteria and detailed design still need approval.
Material TBDs block implementation. Approval revision, approvers and evidence: TBD.

No security mechanism is implemented or verified by this document.

## AI assets

Inventory prompts, context, outputs, credentials, metadata and owners: TBD.

## Trust boundaries

Map client/service, storage/context, context/model, output/UI and agent handoffs.
Data flows and enforcement points: TBD.

## Attack surfaces

User text, stored reports, playbooks, output, gateway, logs and agent messages:
inventory/exposure TBD.

## Direct prompt injection

Instruction bypass, disclosure and unauthorized-data payloads; expected safe
outcomes and test cases: TBD.

## Indirect prompt injection

Stored/retrieved instructions are untrusted data. Detailed cases, provenance
checks and expected outcomes: TBD.

## Malicious retrieved content

Approved context sources/provenance policy: TBD. Existing planning uses approved
maintenance playbooks; no broad retrieval system is implemented.

## Model output validation

Typed schemas, allowlisted values, unknown fields, escaping and unsafe advice
handling: TBD. No validator exists yet.

## Authorization boundaries

AGENTS.md requires deterministic server authorization. AI cannot submit, assign,
reprioritize, transition or close records or bypass service checks. Per-endpoint
and record-level acceptance criteria: TBD.

## Least privilege

Bound context to authorized role/task; no model mutation tools. Exact fields,
credential scopes and development-agent permissions: TBD.

## Sensitive data handling

Minimize context/retention; never put credentials in prompts/logs. Classification,
redaction, retention and deletion criteria: TBD.

## API/resource limits

SoC LLM per-user/global quotas, token/input budgets, concurrency and bounded
retries need design. Exact values/exhaustion behavior: TBD.

## Timeout/failure behaviour

Core workflow must survive AI failure. Deadlines, cancellation, malformed/partial
output, 429 and unavailable-service outcomes: TBD.

## Approval gates

Authenticated humans act through normal deterministic services. Confirmation,
state/version checks and bypass criteria: TBD. Spec, merge and production gates
are process requirements; no application controls are built.

## Human oversight

Users review drafts/recommendations. Rejection/editing UX, uncertainty and safety
escalation: TBD. Teammate verification remains required.

## Auditability

Minimal fields, prompt/version provenance, evidence and safe retention: TBD.
No AI observability is implemented.

## Security test scenarios

Plan direct/indirect/obfuscated injection, malicious context, cross-account/role
exposure, malformed/oversized output, bypass, exhaustion, concurrent calls,
timeouts and log leakage. Expected results: TBD. Use the security-test template;
record actual execution separately.

## Unresolved questions

Severity, exact controls, criteria, data scope/retention, quotas, timeouts/retries
and approval evidence: TBD.
