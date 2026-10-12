# FacilityFlow AI MP3 Plan

Status: planning baseline updated 10 October 2026. The product direction, MP2
reuse permission, and role-specific AI concepts are confirmed by the user; the
detailed specifications still require teammate review.

The full planning document is stored at
[`../docs/planning/MP3_First_Group_Planning_Document.docx`](../docs/planning/MP3_First_Group_Planning_Document.docx).

## Approved decisions

- Build FacilityFlow AI as a server-rendered modular monolith using Spring Boot,
  Thymeleaf/HTMX, PostgreSQL, Spring Security, and Docker.
- Preserve the MP2 Requester, Technician, and Facilities Manager domain and
  lifecycle while replacing JavaFX and SQLite.
- Reuse is permitted under the lecturer clarification reported by the team on
  10 October 2026. Record every reused component and quantify final reuse.
- Provide a Smart Report Assistant for Requesters, a Work Plan Assistant for
  Technicians, and a Triage Assistant for Facilities Managers.
- AI output is advisory and cannot call mutation tools or perform business
  actions. Each user reviews suggestions before using ordinary authorized
  services.
- Maintain separate development and production services, databases,
  configuration, secrets, and hostnames.
- Build and test one container image in GitHub Actions, deploy it to development,
  run smoke tests, and require human approval before promoting the same digest
  to production.

## Working allocation

| Member | Primary role focus | AI feature | Shared area coordinated by member |
| --- | --- | --- | --- |
| `yooplo` | Requester | Report Assistant | Authentication, sessions, role routing, CSRF, account access |
| `ngkhengyang` | Technician | Work Plan Assistant | SoC LLM gateway, rate limiting, typed output, observability |
| `yu-sutong` | Facilities Manager | Triage Assistant | PostgreSQL migrations, audit model, CI/CD, deployment |

This allocation supports coordination and may change. It does not restrict a
member to one role or make shared work exclusive. Contribution evidence should
record the work actually completed and reviewed.

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

- Obtain teammate approval for the updated role AI contracts and shared AI
  security requirements.
- Add the first reused MP2 files to the ledger before merging them into MP3.
- Decide exact SoC LLM quotas, timeouts, retry limits, retention, and approved
  maintenance playbook sources.
- Approve the remaining product, architecture, data, and deployment details.
- Bootstrap the Spring/PostgreSQL walking skeleton only after the specification
  gate passes.

## Development phases and exit gates

Phases describe planned work, not completed implementation. Dated milestones
above remain the baseline and are not completion evidence.

| Phase | Purpose | Exit evidence | Current status |
| --- | --- | --- | --- |
| 0 - Repository and workflow setup | Docs, templates and config audit | Validation and teammate review | Scaffold prepared; review pending |
| 1 - Requirements/specification | Behavior, IDs, criteria and exclusions | Role owner/teammate approve revisions | Pending |
| 2 - Architecture and security design | Detail stack, contracts and threats | Design/spec/decision approval | Pending detailed design |
| 3 - Core application foundation | Approved framework, auth, storage and CI | Tests and independent review | Not implemented |
| 4 - Role-specific features | Approved three-role workflows | Requirement-linked tests | Not implemented |
| 5 - AI features | One bounded SoC LLM feature per role | Schema, limit and failure evidence | Not implemented |
| 6 - AI security hardening | Injection, isolation, output and gate controls | Adversarial tests and fresh verification | Not implemented |
| 7 - Integration and testing | Cross-role lifecycle and regression | Results, traceability and teammate review | Not implemented |
| 8 - Deployment | Independent automation and environment separation | Dev smoke tests, human promotion, rollback evidence | Not implemented |
| 9 - Documentation, reflection and release | Guides, reuse, logs and submission | Reviewed release on master | Pending |

Security work starts before coding and continues through every phase. Production
deployment is independent of AI coding/build-host environments. Development and
production remain separate. GitHub Actions/Render are existing planning choices;
secrets use GitHub/deployment-platform secret storage. No deployment automation
or live environment is created during phase 0.
