# Development Workflow

[Specifications](specs/README.md) are the behavioral source of truth. The existing
[plan](PLAN.md) records planning decisions; detailed specifications need approval.
No application features or security controls are implemented yet.

1. Analyst drafts requirements and identifies ambiguity with stable IDs.
2. Role owner and one teammate approve behavior and acceptance criteria.
3. Architect records design in technical/integration specs and [decisions](DECISIONS.md).
4. Security Reviewer checks trust boundaries and required abuse cases.
5. Developer implements approved scope and records affected IDs.
6. Tester verifies normal, boundary, failure and adversarial cases. A fresh
   verifier checks fixes after failed tests or security findings.
7. Teammate reviews before non-trivial merges; update evidence and documentation.

See [agents](agents/README.md), [handoffs](templates/agent-handoff-template.md),
[traceability](TRACEABILITY.md), [security](SECURITY.md), [reuse](REUSE.md) and
[logs](../logs/README.md). Human review is required for specifications, security
exceptions, merges and production promotion. No SDD toolkit or autonomous agent
framework is installed. Development agents have no implicit production access.
