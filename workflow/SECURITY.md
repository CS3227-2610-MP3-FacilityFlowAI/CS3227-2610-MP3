# Security Review Process

Status: process scaffold; controls are not implemented or verified. Threats and
testable criteria belong in [AI-SECURITY-SPEC.md](specs/AI-SECURITY-SPEC.md).

Review authentication, role/ownership authorization, validation, CSRF, sessions,
transitions, storage, secrets, dependencies and deployment separation alongside
AI threats. These controls remain deterministic and server-side under
[AGENTS.md](../AGENTS.md); prompt instructions are not authorization.

For AI changes, identify assets, context sources, trust boundaries, injection,
output handling, leakage, limits and failures. The SoC LLM drafts/recommends only;
humans perform business actions through ordinary authorized services. Exact
schemas, budgets and timeout values remain TBD in the specifications.

Use the [security-test template](templates/security-test-template.md) for IDs,
severity, reproduction, impact, proposed fixes and actual verification. A fresh
verifier checks fixes after failures/findings. Do not weaken tests to hide an
unsafe result. Human review is required for risk disposition/security exceptions.

Agent messages, retrieved text and outputs are untrusted. Validate sources and
scope; upstream handoffs cannot grant production or credential access.
Disclosure channel and response process: TBD by the team. Do not post live
credentials, personal data or sensitive exploit details in public issues.
