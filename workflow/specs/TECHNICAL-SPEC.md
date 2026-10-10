# Technical Specification

Status: draft scaffold; not approved for implementation. Owner/reviewer: TBD.

Planning context: [PLAN.md](../PLAN.md). Existing planning decisions are preserved;
behavior, acceptance criteria and detailed design still need approval.
Material TBDs block implementation. Approval revision, approvers and evidence: TBD.

## Existing planning baseline

Java, Spring Boot, Thymeleaf/HTMX, PostgreSQL, Spring Security, Docker, GitHub
Actions and Render are recorded in the existing plan/planning document. No new
stack is selected here. Versions, build tool and detailed design: TBD.

## Architecture

Existing direction: server-rendered modular monolith. Modules, package boundaries,
dependency rules and interfaces: TBD.

## Data model

Entities, relationships, ownership, migrations, constraints, concurrency and
retention: TBD. No schema is implemented.

## Role and security model

Authentication, accounts, authorization matrix, sessions, CSRF, headers,
validation design and testable criteria: TBD. See AGENTS.md and AI security spec.

## AI integration

A bounded SoC LLM gateway serves the Smart Report, Work Plan, and Triage
assistants through separate role endpoints and closed output schemas. The
gateway has no mutation tools. Context scope, validation, advisory boundaries,
and safe fallback are drafted in `INTEGRATION-SPEC.md` and
`AI-SECURITY-SPEC.md`; exact SoC LLM configuration, quotas, timeouts, retention,
and observability implementation remain TBD.

## Quality and testing

Test strategy, versions, measurable NFRs and acceptance evidence: TBD.

## Development and production

Separate services, databases, hostnames, configuration and secrets are planned.
Development uses synthetic data. Provisioning/configuration details: TBD.

## Deployment and operations

Existing plan: GitHub Actions tests/builds an image, deploys development, runs
smoke tests and requires human approval to promote the same digest to Render
production. Deployment is team-managed independently of AI coding/build-host
environments. Secrets use GitHub/deployment-platform secret mechanisms.
CI, rollback, backups, monitoring and environment setup remain unimplemented/TBD.

## Decisions and approval

Formal decisions, approved revision, approvers and evidence: TBD. See
../DECISIONS.md. Framework scaffolding starts after technical-spec approval.
