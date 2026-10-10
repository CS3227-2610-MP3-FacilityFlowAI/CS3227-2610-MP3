# Requirement Traceability

Status: AI requirements allocated 10 October 2026. They remain draft until
teammate approval and have no implementation or executed-test evidence.

| Requirement ID | Specification | Implementation | Tests | Status |
| --- | --- | --- | --- | --- |
| REQ-RQ-001 | [Requester Smart Report Assistant](specs/ROLE-REQUESTER.md#requirement-req-rq-001) | TBD | Planned role contract, isolation, injection, failure and no-mutation tests | Draft; teammate review pending |
| REQ-TECH-001 | [Technician Work Plan Assistant](specs/ROLE-TECHNICIAN.md#requirement-req-tech-001) | TBD | Planned assignment, state, indirect injection, unsafe output, failure and no-mutation tests | Draft; teammate review pending |
| REQ-MGR-001 | [Manager Triage Assistant](specs/ROLE-MANAGER.md#requirement-req-mgr-001) | TBD | Planned manager authorization, allowlist, stale state, failure and no-mutation tests | Draft; teammate review pending |
| REQ-AI-001 | [Advisory boundary](specs/PRODUCT-SPEC.md#req-ai-001-advisory-boundary) | TBD | Planned direct mutation and approval-bypass tests | Draft; teammate review pending |
| REQ-AI-002 | [Core workflow fallback](specs/PRODUCT-SPEC.md#req-ai-002-core-workflow-fallback) | TBD | Planned timeout, 429, malformed output and unavailable-service tests | Draft; teammate review pending |
| AI-SEC-001 | [Authorization before context](specs/AI-SECURITY-SPEC.md#ai-sec-001-authorization-before-context) | TBD | Planned cross-account, cross-role and denied-before-call tests | Draft; teammate review pending |
| AI-SEC-002 | [Typed output and allowlists](specs/AI-SECURITY-SPEC.md#ai-sec-002-typed-output-and-allowlists) | TBD | Planned enum, identifier, type, unknown-field and escaping tests | Draft; teammate review pending |
| AI-SEC-003 | [Injection resistance](specs/AI-SECURITY-SPEC.md#ai-sec-003-injection-resistance-and-context-provenance) | TBD | Planned direct, indirect, obfuscated, encoded and Unicode injection tests | Draft; teammate review pending |
| AI-SEC-004 | [Limits and failure containment](specs/AI-SECURITY-SPEC.md#ai-sec-004-limits-and-failure-containment) | TBD | Planned input, output, concurrency, timeout, retry and exhaustion tests | Draft; teammate review pending |
| AI-SEC-005 | [Approval and audit boundary](specs/AI-SECURITY-SPEC.md#ai-sec-005-approval-and-audit-boundary) | TBD | Planned CSRF, stale version, role/assignment change, no-mutation and log-leakage tests | Draft; teammate review pending |

Add implementation links and actual test evidence in the same change that
implements a requirement. Suggested states are draft, approved, implemented,
verified, deferred, and superseded. Preserve IDs across revisions and retain
superseded history.
