# SoCLaaS Provider Core Developer Handoff

- Source agent: Analyst and Architect via primary coordinator.
- Destination agent: Developer.
- Task/goal: Implement iteration 2A provider-neutral core: internal port, bounded envelope decoder and model-readiness state machine.
- Approved requirement IDs: REQ-AI-002–006; AI-SEC-005–006, AI-SEC-010, AI-SEC-020.
- Approved specification revision/evidence: v0.2 plus iteration 2A stakeholder direction/approval on 16 October 2026; specification Security Reviewer and teammate review pending.
- Authorized scope/permissions: production Java only under `src/main/java/sg/edu/nus/facilityflow/ai/provider/**`. Do not edit tests, specs, configuration, build files, migrations, role modules, logs or documentation.
- Relevant input artifacts/provenance: `workflow/specs/INTEGRATION-SPEC.md`, `workflow/specs/AI-SECURITY-SPEC.md`, `workflow/TRACEABILITY.md`, `logs/handoffs/2026-10-16-soclaas-provider-core-architect.md`, and `logs/handoffs/2026-10-16-soclaas-provider-core-decisions.md`.
- Assumptions: Jackson is supplied by the existing Spring Web dependency. The provider port is asynchronous. Raw provider bodies remain local to the provider module. The test fake will be implemented separately under the test source set.
- Unresolved questions: exhaustive HTTP status mapping and the real HTTPS adapter are intentionally deferred.
- Files changed: specification and handoff records only before development.
- Commands/tools: Analyst ran `git diff --check`; no production implementation or tests exist for iteration 2A yet.
- Verification performed/results/evidence: specification diff is whitespace-clean; runtime behavior is unverified.
- Security considerations/unresolved risks: enforce decompressed max-plus-one size, strict UTF-8 and JSON constraints before semantic decoding; expose only stable safe failure codes; never retain raw body/parser excerpts; isolate caller cancellation from shared readiness.
- Suggestions accepted/rejected: accepted the Architect's deep provider module, exact readiness state machine and deterministic-fake seam; rejected live networking, retries, rate limits, prompts, telemetry and role endpoints in this increment.
- Human approval required and reason: Security Reviewer and teammate review remain required before merge.
- Human reviewer/outcome/evidence: stakeholder authorized implementation using the recommended decisions; team review pending.
- Next authorized action: Developer implements only the production provider-core scope and reports every changed file. A separate unit-test agent then owns tests.
- Status: ready for development.
