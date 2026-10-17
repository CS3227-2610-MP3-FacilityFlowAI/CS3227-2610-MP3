# SoCLaaS HTTPS Adapter Developer Handoff

- Source agent: Architect and primary coordinator.
- Destination agent: HTTPS adapter developer.
- Task/goal: Implement Iteration 2B production SoCLaaS HTTPS transport and closed status mapping behind the package-private provider port.
- Approved requirement IDs: REQ-AI-001–006; AI-SEC-008–010, AI-SEC-019–020.
- Approved specification/evidence: Iteration 2B additions to `workflow/specs/INTEGRATION-SPEC.md`, `workflow/specs/AI-SECURITY-SPEC.md` and `workflow/TRACEABILITY.md`, based on provider-core commit `897937d`.
- Authorized scope: production adapter/configuration integration under `src/main/java/sg/edu/nus/facilityflow/ai/provider/**` plus the narrow credential capability in `AiConfiguration.Available`; no Spring wiring, retries, rate limits, telemetry, role gateway or schema changes.
- Required controls: canonical `/v1/models` and `/v1/chat/completions` endpoints; JDK HTTPS with redirects disabled and default TLS; exact JSON and headers; no raw-key getter; minimum configured/request deadline; cancellation propagation; identity-only response handoff to the existing bounded decoder; closed status and `Retry-After` mapping; safe diagnostics and body closure.
- Deferred scope: retry execution, rate/lease enforcement, compressed decompression and wire-size limits, telemetry, Spring lifecycle wiring and role-facing gateway/coordinator.
- Verification expectation: focused adapter tests, compile/full checks where available, `git diff --check`, and a fresh Security Reviewer pass before commit.
- Verification: focused adapter suite passed 23/23 after credential-capability and malformed-response remediations; `git diff --check` is clean. Fresh Security Reviewer returned GO on 16 October 2026.
- Status: implementation, tests and security review complete; ready to commit.
