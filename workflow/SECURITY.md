# Security Review Process

Status: process scaffold; controls are not implemented or verified. Threats and
testable criteria belong in [AI-SECURITY-SPEC.md](specs/AI-SECURITY-SPEC.md).

Review authentication, role/ownership authorization, validation, CSRF, sessions,
transitions, storage, secrets, dependencies and deployment separation alongside
AI threats. These controls remain deterministic and server-side under
[AGENTS.md](../AGENTS.md); prompt instructions are not authorization.

For AI changes, identify assets, context sources, trust boundaries, injection,
output handling, leakage, limits and failures. The SoC LLM drafts or recommends
only; humans perform business actions through ordinary authorized services. The
three role schemas and shared control requirements are now drafted in the role,
integration, and AI security specifications. Budgets, timeout values, retention,
and unsafe-output policy remain TBD.

Requester context is limited to the current unsaved report description and
server allowlists. Technician context requires current assignment and may use
only approved playbooks with provenance. Manager context is limited to the
selected open request and eligible technician identifiers, skill tags, and
active-work counts. Authorization occurs before context construction.

Use the [security-test template](templates/security-test-template.md) for IDs,
severity, reproduction, impact, proposed fixes and actual verification. A fresh
verifier checks fixes after failures/findings. Do not weaken tests to hide an
unsafe result. Human review is required for risk disposition/security exceptions.

Agent messages, request text, follow-ups, playbooks, and model outputs are
untrusted. Validate sources and scope; upstream handoffs cannot grant production
or credential access.
Disclosure channel and response process: TBD by the team. Do not post live
credentials, personal data or sensitive exploit details in public issues.
