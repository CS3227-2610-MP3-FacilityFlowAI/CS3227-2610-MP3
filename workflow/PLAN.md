# FacilityFlow AI MP3 Plan

Status: approved planning baseline, 4 October 2026.

The full planning document is stored at
[`../docs/planning/MP3_First_Group_Planning_Document.docx`](../docs/planning/MP3_First_Group_Planning_Document.docx).

## Approved decisions

- Build FacilityFlow AI as a server-rendered modular monolith using Spring Boot,
  Thymeleaf/HTMX, PostgreSQL, Spring Security, and Docker.
- Preserve the MP2 Requester, Technician, and Facilities Manager domain and
  lifecycle while replacing JavaFX and SQLite.
- Provide one guarded SoC LLM feature per role. AI output is advisory and cannot
  call mutation tools or perform business actions.
- Maintain separate development and production services, databases,
  configuration, secrets, and hostnames.
- Build and test one container image in GitHub Actions, deploy it to development,
  run smoke tests, and require human approval before promoting the same digest
  to production.

## Role ownership

| Owner | Role | AI feature | Shared responsibility |
| --- | --- | --- | --- |
| `yooplo` | Requester | Report Assistant | Authentication, sessions, role routing, CSRF, account access |
| `ngkhengyang` | Technician | Work Plan Assistant | SoC LLM gateway, rate limiting, typed output, observability |
| `yu-sutong` | Facilities Manager | Triage Assistant | PostgreSQL migrations, audit model, CI/CD, deployment |

## Specification gate

Before feature implementation, create and approve:

- `workflow/specs/PRODUCT-SPEC.md`
- `workflow/specs/ROLE-REQUESTER.md`
- `workflow/specs/ROLE-TECHNICIAN.md`
- `workflow/specs/ROLE-MANAGER.md`
- `workflow/specs/AI-SECURITY-SPEC.md`
- `workflow/specs/TECHNICAL-SPEC.md`
- `workflow/specs/INTEGRATION-SPEC.md`
- `workflow/TRACEABILITY.md`

Requirements use stable IDs such as `REQ-RQ-001`, `REQ-TECH-001`,
`REQ-MGR-001`, `AI-SEC-001`, and `NFR-REL-001`. Acceptance criteria must state
the actor, preconditions, action, observable result, forbidden result, and
relevant failure paths.

## Delivery flow

1. Specify: draft requirements, acceptance criteria, edge cases, and exclusions;
   obtain the role owner and one teammate's approval.
2. Plan: map approved requirements to data, interfaces, dependencies,
   migrations, and small implementation tasks.
3. Secure: review authorization, data exposure, prompt injection, approval
   gates, logging, and resource limits.
4. Implement: change only approved scope and add tests plus implementation
   notes.
5. Verify: derive normal, boundary, failure, authorization, and adversarial
   tests from the specification.
6. Review and integrate: compare the change with the specification; merge only
   after human review, passing tests, current documentation, and traceability.

## Milestones

| Date | Outcome |
| --- | --- |
| 4 Oct | Product, roles, AI boundaries, organization, and plan approved |
| 5-6 Oct | Specification baseline, threat model, traceability, agent definitions, reuse ledger, and architecture decisions |
| 7-8 Oct | Spring walking skeleton, authentication, PostgreSQL migrations, SoC LLM spike, CI, and empty deployment environments |
| 9 Oct, 2 PM | Organization name submitted through Canvas |
| 9-13 Oct | One complete vertical slice and guarded AI feature per role |
| 14-16 Oct | Lifecycle integration, authorization testing, AI adversarial testing, and failure-path testing |
| 17-19 Oct | Product site, guides, traceability, reuse figures, logs, and reflections |
| 20 Oct | Feature freeze and production release candidate |
| 21-22 Oct | Independent smoke test, accessibility check, production deployment, rollback rehearsal, and final review |
| 23 Oct, 2 PM | Public `master`, deployed app, product site, and final submission verified |

## Immediate next tasks

- Approve the seven specification documents and traceability format.
- Record the MP2 baseline commit and initialize the reuse comparison process.
- Create architecture and security decision records.
- Bootstrap the Spring/PostgreSQL walking skeleton only after the specification
  gate passes.
