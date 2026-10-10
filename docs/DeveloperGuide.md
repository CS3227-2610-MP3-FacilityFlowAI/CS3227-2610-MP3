# Developer Guide

Status: pre-implementation placeholder.

This guide will describe the released architecture, data model, security model,
SoC LLM integration, specification-driven workflow, multi-agent workflow,
testing, deployment, operations, and acknowledged reuse. It must match the
latest release rather than the intended design.

## Project overview

FacilityFlow AI, CS3227 MP3. Planning/scaffolding only; see README and AGENTS.md.

## Repository structure

src/: future code/tests; workflow/: specs/process; docs/: guides/planning;
logs/: summaries; .github/: PR-Agent/contribution template. No app build exists.

## Architecture

Existing plan: Java/Spring Boot modular monolith, Thymeleaf/HTMX, Spring
Security, PostgreSQL and Docker. Detailed design/build tool/versions: TBD
in ../workflow/specs/TECHNICAL-SPEC.md.

## Data model

Entities, relationships, migrations, invariants and concurrency: TBD.

## Role model

Requester: primary focus coordinated by `yooplo`; Technician: primary focus
coordinated by `ngkhengyang`; Manager: primary focus coordinated by
`yu-sutong`. These focuses organize work and are not exclusive code ownership.
Detailed ordinary permissions and account lifecycle remain TBD in role specs.

## Security design

Deterministic server-side auth, authorization, validation, transitions and
writes are required. Detailed controls and implementation evidence: TBD.

## AI integration

The planned shared SoC LLM gateway serves three advisory features:

- Smart Report Assistant for Requester draft structure, category, urgency,
  missing-information questions, and safety advice.
- Work Plan Assistant for Technician safety checks, diagnostics, tools,
  requester questions, and evidence to collect.
- Triage Assistant for Manager category, priority, clarification, and eligible
  technician recommendations.

Each role has a separate endpoint, typed schema, validator, authorization check,
and fallback. The gateway has no mutation tools. The model and environment
configuration, exact limits, and retention remain TBD in
`INTEGRATION-SPEC.md`.

## AI security

Prompts/context/output are untrusted. Scope, least privilege, typed validation,
limits and fallback need design/testing. See AI-SECURITY-SPEC.md and
../workflow/SECURITY.md. No controls are implemented yet.

## SDD workflow

Use ../workflow/README.md: specify, approve, design, secure, implement, verify,
review. Stable IDs/traceability are required; material TBDs block coding.

## Multi-Agent SE workflow

Use ../workflow/agents/README.md and handoff template. Bounded roles may run
sequentially; no framework is installed. Fresh verification follows fixes to
failures/findings. Definitions are not evidence of actual agent invocation.

## Testing strategy

Derive normal, boundary, failure, authorization, integration and adversarial
cases from requirements. Tooling/commands: TBD after design approval. Record
executed tests separately from planned/added tests.

## Development vs production environments

Separate services, databases, hostnames, config and secrets are planned.
Synthetic development data only. Setup and variable names: TBD; no speculative
.env.example is introduced.

## Deployment

Team-managed deployment must be independent of AI coding/build-host environments.
Existing plan uses GitHub Actions, immutable image promotion and Render; humans
approve production. Secrets use GitHub/deployment-platform mechanisms.
Provisioning, workflows, backups and rollback remain TBD/unimplemented.

## MP2 reuse

The team reported lecturer confirmation that MP2 reuse is permitted when MP3 is
a deployed web application with a production database and documented reuse.
The source baseline commit is verified in `../workflow/REUSE.md`. Measure code,
tests, and documentation separately and acknowledge every source before merge.

## Acknowledgements

Team: ../AGENTS.md. AI evidence: ../logs/. Reused material: reuse ledger.
Other source/dependency acknowledgements: TBD.
