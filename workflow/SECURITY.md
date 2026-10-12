# Security Review Process

Status: authentication controls and the AI authorization-first seam were
implemented and locally tested on 13 October 2026; teammate review, assistant
endpoint controls and wider release verification remain pending. Threats and
testable criteria belong in [AI-SECURITY-SPEC.md](specs/AI-SECURITY-SPEC.md).

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
Real PostgreSQL, browser, account-administration and complete AI endpoint testing
remain required.
