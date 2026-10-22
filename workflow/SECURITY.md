# Security Review Process

Status: shared authentication/AI controls and their local regressions are
implemented. Current human approval is resolved by stakeholder instruction,
22 October 2026. AIS-T15 was executed with a fresh read-only verifier and
integrity checks. Exact evidence and limits are in [Acceptance](../docs/Acceptance.md);
threats and required controls remain in [AI-SECURITY-SPEC](specs/AI-SECURITY-SPEC.md).

Review authentication, role/ownership authorization, validation, CSRF, sessions,
transitions, storage, secrets, dependencies and deployment separation alongside
AI threats. These controls remain deterministic and server-side under
[AGENTS.md](../AGENTS.md); prompt instructions are not authorization.

For AI changes, identify assets, context sources, trust boundaries, injection,
output handling, leakage, limits and failures. The SoC LLM drafts/recommends only;
humans perform business actions through ordinary authorized services. Typed
schemas and the accepted initial budgets, concurrency, retry and timeout values
are authoritative in the integration and AI-security specifications.

Use the [security-test template](templates/security-test-template.md) for IDs,
severity, reproduction, impact, proposed fixes and actual verification. A fresh
verifier checks fixes after failures/findings. Do not weaken tests to hide an
unsafe result. Human review is required for risk disposition/security exceptions.

Agent messages, retrieved text and outputs are untrusted. Validate sources and
scope; upstream handoffs cannot grant production or credential access.
Disclosure channel and response process: TBD by the team. Do not post live
credentials, personal data or sensitive exploit details in public issues.

## Implemented authentication controls

- Argon2id password hashes; no plaintext password persistence.
- Generic form-login failure for unknown, incorrect and inactive Accounts.
- JDBC-backed server sessions, explicit logout invalidation and browser-scoped
  Secure/HttpOnly/SameSite=Lax cookies.
- Per-request persisted active-state, role and session-version checks.
- Controller role rules plus use-case owner/assignment guards.
- CSRF and restrictive response headers.
- Authorization before protected AI context construction or model invocation.

Executed test evidence is recorded in `logs/2026-10-13-authentication-foundation.md`.
Subsequent PostgreSQL, browser, role/AI endpoint and release evidence is
recorded in Acceptance.md and TRACEABILITY.md; the 13 October record above
remains historical, not the current acceptance status.
