# Specification Draft

Status: tool-verified summary prepared by Codex; human and teammate review
pending. Project date/timezone: 12 October 2026, Asia/Singapore. Codex acted as
the Analyst and Architect; no subagents were used.

## Goal

Create the initial FacilityFlow AI specification baseline without implementing
the application. Preserve confirmed MP2 behavior while adapting FacilityFlow to
a deployed web application with PostgreSQL, one guarded SoCLaaS feature for each
role, Vercel Preview and Production environments, and bounded development-agent
roles that receive only the context required for their tasks.

## Authority and sources

- Stakeholder decisions confirmed through 12 October 2026.
- The binding group-planning document.
- MP2 `master` commit `9819b857d880134c1fc1ffef8a8bcd5d6e536f43`.
- Local SoCLaaS access, API, GUI, and quick-start documentation.
- Local lecture notes on AI-agent security, specification-driven development,
  and multi-agent software engineering.

The resulting specification revision was draft v0.1 and remained subject to
pull-request and teammate approval.

## Changes

- Drafted shared product, Requester, Technician, Facilities Manager, AI
  integration, AI security, and technical specifications.
- Defined stable IDs across the `REQ-SH`, `REQ-RQ`, `REQ-TECH`, `REQ-MGR`,
  `REQ-AI`, `AI-SEC`, and `NFR` families.
- Added requirement-to-test and requirement-to-artifact traceability.
- Updated planning, reuse, security, workflow, developer-guide, and repository
  navigation material.
- Added the Spec and Documentation Verifier and Integration and Release
  Verifier roles required by the binding seven-role plan.

The affected areas were `CONTEXT.md`, `README.md`, `docs/DeveloperGuide.md`,
`workflow/specs/`, `workflow/TRACEABILITY.md`, `workflow/PLAN.md`,
`workflow/REUSE.md`, `workflow/SECURITY.md`, `workflow/README.md`, and
`workflow/agents/`.

## Decisions and AI suggestions

Shared behavior belongs in `PRODUCT-SPEC.md`; role specifications own
role-specific behavior and human approval; `INTEGRATION-SPEC.md` owns typed
SoCLaaS contracts; `AI-SECURITY-SPEC.md` owns AI threats and controls; and
`TECHNICAL-SPEC.md` owns module, persistence, security, and Vercel design.

Accepted suggestions included:

- Deep module boundaries and minimum-reading routes for implementing agents.
- Strict, typed AI output schemas and fail-closed validation.
- Deterministic Technician candidate ranking outside the LLM.
- PostgreSQL-backed sessions and shared limits where distributed execution
  requires them.
- Sanitized, evidence-bearing agent handoffs.

Rejected or deferred suggestions included:

- Autonomous business or development orchestration.
- Application implementation during specification drafting.
- Production access or deployment changes.
- Tool-enabled application LLM behavior.
- Repeating the complete shared specification context in every role document.

## Verification

- The initial draft contained 125 unique requirement IDs.
- Every initial requirement ID was represented in `workflow/TRACEABILITY.md`.
- Checked local Markdown targets were present.
- No material specification placeholders remained in the reviewed baseline.
- `git diff --check` passed.

These checks covered documentation structure and consistency only. No
application code, database migration, SoCLaaS call, security control, deployment,
or application test was executed. The later `master` merge and its resulting
128-requirement check are recorded separately in
`logs/2026-10-12-master-merge-resolution.md`.

## Corrections and unresolved items

The draft reconciled Technician account handling with the then-reviewed MP2
semantics and added the two missing specialist verifier definitions. Subsequent
stakeholder-approved corrections remain governed by the current specifications
rather than this historical session record.

Provider credentials, deployed URLs, model choice, quotas, managed PostgreSQL
vendor, and exact compatible dependency versions remained configuration-time
values. Vercel and Spring Boot feasibility, operational limits, and drafted
non-functional targets still required execution evidence.

## Security notes

No credentials, raw prompts, or raw model outputs were recorded. The drafted AI
features remained advisory, and authentication, authorization, lifecycle rules,
validation, and business mutations remained deterministic application
responsibilities. Production access and autonomous agent approval were outside
the session scope.

## Human review

The draft recorded stakeholder direction but did not constitute teammate
approval. Pull-request review and the approvals named in the specification
documents remained required before implementation.
