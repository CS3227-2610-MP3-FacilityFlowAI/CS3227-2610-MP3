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

Requester: yooplo; Technician: ngkhengyang; Manager: yu-sutong. Detailed
permissions, ownership and account lifecycle: TBD in role specs.

## Security design

Deterministic server-side auth, authorization, validation, transitions and
writes are required. Detailed controls and implementation evidence: TBD.

## AI integration

SoC LLM advisory gateway is planned. Contract, model, schemas and environment
configuration: TBD in INTEGRATION-SPEC.md.

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

See ../workflow/REUSE.md; verify source commit before copying. Measure code,
tests/docs separately and acknowledge every source.

## Acknowledgements

Team: ../AGENTS.md. AI evidence: ../logs/. Reused material: reuse ledger.
Other source/dependency acknowledgements: TBD.
