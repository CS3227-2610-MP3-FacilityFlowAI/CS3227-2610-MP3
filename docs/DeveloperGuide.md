# Developer Guide

Status: authentication foundation implemented 13 October 2026; teammate review
pending. This guide distinguishes implemented behavior from approved future work.

This guide will describe the released architecture, data model, security model,
SoC LLM integration, specification-driven workflow, multi-agent workflow,
testing, deployment, operations, and acknowledged reuse. It must match the
latest release rather than the intended design.

## Project overview

FacilityFlow AI, CS3227 MP3. The repository now contains a Java 25/Spring Boot
web walking skeleton with authentication. See README and AGENTS.md.

## Repository structure

`src/main/java`: application modules; `src/main/resources`: templates,
configuration and Flyway migrations; `src/test`: automated tests; `workflow`:
specs/process; `docs`: guides/planning; `logs`: verified session summaries.

## Architecture

The implemented foundation pins Java 25, Gradle 9.1.0 and Spring Boot 4.1.1.
Spring MVC/Thymeleaf serves pages, Spring Security controls authentication,
Spring Session JDBC persists browser sessions, Flyway owns schema changes, and
JDBC repositories isolate PostgreSQL access. The approved wider modular-monolith
design remains in `../workflow/specs/TECHNICAL-SPEC.md`.

## Data model

Migration `V1__create_identity_and_session_tables.sql` owns `user_account`,
`spring_session` and `spring_session_attributes`. `user_account` includes the
immutable UUID, original and normalized username, display name, Argon2id hash,
one role, active flag, session version and timestamps. Later database work must
extend this migration history rather than editing an applied migration.

## Role model

Requester owner: `yooplo`; Technician owner: `ngkhengyang`; Facilities Manager
owner: `yu-sutong`. Members may contribute across slices, but each owner remains
accountable for completing their role features. Detailed permissions and
Account lifecycle rules are defined in the shared and role specifications.

## Security design

Spring Security form login uses one generic invalid-credentials result for an
unknown username, incorrect password or inactive account. Argon2id parameters
are configuration-pinned. Every authenticated request reloads active state,
session version and role from persistence. Role URL rules are backed by
`IdentityAuthorization` owner/assignment guards at use-case seams; UI hiding is
not authorization. CSRF remains enabled, logout invalidates the JDBC session,
and the `SESSION` cookie is HttpOnly, SameSite=Lax, browser-scoped and Secure by
default. Restrictive CSP, frame, MIME, referrer and HSTS headers are configured.

## AI integration

The planned shared SoC LLM gateway serves three advisory features:

- Smart Report Assistant for Requester draft structure, category, urgency, and
  missing-information questions.
- Work Plan Assistant for Technician safety checks, diagnostics, tools,
  requester questions, and evidence to collect.
- Triage Assistant for Manager category, priority, clarification, and skill-tag
  suggestions; the application ranks eligible Technicians deterministically.

Each role has a separate endpoint, typed schema, validator, authorization check,
and fallback. The gateway has no mutation tools. The model, environment
configuration, initial limits, retention and typed results are defined in
`../workflow/specs/INTEGRATION-SPEC.md` and
`../workflow/specs/AI-SECURITY-SPEC.md`.

## AI security

Prompts/context/output are untrusted. `AiAccessGuard` implements the
authorization-before-context seam required by AI-SEC-001 and is verified to
construct no protected context and make no model call after denial. Typed
schemas, limits, transport and assistant endpoints remain unimplemented. See
AI-SECURITY-SPEC.md and ../workflow/SECURITY.md.

## SDD workflow

Use ../workflow/README.md: specify, approve, design, secure, implement, verify,
review. Stable IDs/traceability are required; material TBDs block coding.

## Multi-Agent SE workflow

Use ../workflow/agents/README.md and handoff template. Bounded roles may run
sequentially; no framework is installed. Fresh verification follows fixes to
failures/findings. Definitions are not evidence of actual agent invocation.

## Testing strategy

Run all current tests with:

```sh
./gradlew test
```

The wrapper downloads the Java 25 toolchain when it is not installed. Current
tests run Flyway against an isolated H2 database in PostgreSQL compatibility
mode and cover login, role routes, CSRF, sessions, password storage,
persisted-account changes, object authorization and the AI access boundary.
An actual PostgreSQL integration suite and browser tests remain required before
release.

For a local application run, create an empty PostgreSQL database and supply:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/facilityflow
SPRING_DATASOURCE_USERNAME=<local account>
SPRING_DATASOURCE_PASSWORD=<local secret>
SESSION_COOKIE_SECURE=false
```

Then run `./gradlew bootRun`. Do not commit local values. No demo or production
account bootstrap is implemented yet, so an approved initializer/account-
administration slice must provision the first account before interactive use.

## Development vs production environments

Separate services, databases, hostnames, config and secrets are planned. Use
synthetic development data only. Configuration variable names and development/
demo seeding rules are defined in the technical and integration specifications;
no secret values belong in documentation or source control.

## Deployment

Team-managed deployment must be independent of AI coding/build-host environments.
The approved direction uses GitHub Actions and Vercel Preview/Production;
humans approve promotion of a smoke-tested Production candidate without a
rebuild. Secrets use GitHub/deployment-platform mechanisms. Provisioning,
workflows, backups and rollback remain unimplemented.

## MP2 reuse

The team reported lecturer confirmation that MP2 reuse is permitted when MP3 is
a deployed web application with a production database and documented reuse.
The source baseline commit is verified in `../workflow/REUSE.md`. Measure code,
tests, and documentation separately and acknowledge every source before merge.

## Acknowledgements

Team: ../AGENTS.md. AI evidence: ../logs/. Reused material: reuse ledger.
Other source/dependency acknowledgements: TBD.
