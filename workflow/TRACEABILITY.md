# Requirement Traceability

Status: draft specification baseline v0.1, 12 October 2026. No requirement is
approved, implemented or verified by this table.

Ranges may share a row only while every ID has the same source, status and empty
implementation/evidence fields. Split a range before implementation when files,
tests or status differ. Never mark verified without executed command/result or
review evidence.

| Requirement IDs | Authoritative section | Implementation | Test/evidence | Status |
| --- | --- | --- | --- | --- |
| REQ-SH-001–018 | [Product shared requirements](specs/PRODUCT-SPEC.md#shared-functional-requirements) | pending | product acceptance pending | draft |
| NFR-SEC-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | security evidence pending | draft |
| NFR-PERF-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | 50-user performance result pending | draft |
| NFR-A11Y-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | Chrome/accessibility checks pending | draft |
| NFR-REL-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | fallback/restore evidence pending | draft |
| NFR-OPS-001–002 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | monitoring/log evidence pending | draft |
| NFR-DEP-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | deployment separation pending | draft |
| NFR-DOC-001 | [Product quality](specs/PRODUCT-SPEC.md#quality-requirements) | pending | documentation/reuse/process review pending | draft |
| REQ-RQ-001–011 | [Requester manual workflow](specs/ROLE-REQUESTER.md#manual-workflow-requirements) | pending | RQ-A01–A07 pending | draft |
| REQ-RQ-012–017 | [Smart Report Assistant](specs/ROLE-REQUESTER.md#smart-report-assistant) | pending | RQ-AI01–AI03 pending | draft |
| REQ-TECH-001–011 | [Technician manual workflow](specs/ROLE-TECHNICIAN.md#manual-workflow-requirements) | pending | TECH-A01–A06 pending | draft |
| REQ-TECH-012–017 | [Work Plan Assistant](specs/ROLE-TECHNICIAN.md#work-plan-assistant) | pending | TECH-AI01–AI03 pending | draft |
| REQ-MGR-001–012 | [Manager request/review](specs/ROLE-MANAGER.md#request-and-review-requirements) | pending | MGR-A01–A05 pending | draft |
| REQ-MGR-013–015 | [Manager accounts](specs/ROLE-MANAGER.md#account-administration) | pending | MGR-A06 pending | draft |
| REQ-MGR-016–019 | [Category and skill administration](specs/ROLE-MANAGER.md#category-and-skill-administration) | pending | MGR-A07 pending | draft |
| REQ-MGR-020–025 | [Triage Assistant and matching](specs/ROLE-MANAGER.md#triage-assistant-and-deterministic-candidate-ranking) | pending | MGR-A08–A10 pending | draft |
| REQ-AI-001–006 | [SoCLaaS transport](specs/INTEGRATION-SPEC.md#soclaas-transport) | pending | gateway contract tests pending | draft |
| REQ-AI-007–010 | [Usage and audit metadata](specs/INTEGRATION-SPEC.md#usage-and-audit-metadata) | pending | telemetry/redaction tests pending | draft |
| AI-SEC-001–022 | [AI controls](specs/AI-SECURITY-SPEC.md#required-controls) | pending | AIS-T01–T14 pending | draft |
| AI-SEC-023–027 | [Human/process gates](specs/AI-SECURITY-SPEC.md#human-and-development-process-gates) | pending | AIS-T15 and review evidence pending | draft |

## Update rule

The implementing pull request replaces pending with exact source/test paths for
its IDs, records the approved specification commit, and leaves unrelated rows
unchanged. Verification records the environment, command, date, result and
reviewer; a test file without an executed result is implemented, not verified.
