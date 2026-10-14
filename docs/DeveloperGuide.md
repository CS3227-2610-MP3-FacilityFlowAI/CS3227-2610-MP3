# Developer Guide

Status: Build scaffold and database foundation implemented; V1 application and
permissions evidenced by user screenshots. Product features remain pending.

Part 2b supplies the private identity/audit/session schema, restricted-role
provisioning and isolated PostgreSQL migration tests. See
[DatabaseMigrations](DatabaseMigrations.md) for applying the development schema
with separate migration/runtime identities. User-reported connectivity succeeded;
user screenshots confirm V1/table metadata and four permission restrictions;
runtime connection succeeded as reported by the user. No independent hosted
query was performed.

## Local scaffold (Part 1)

Install JDK 25 and run the committed Gradle wrapper from the repository root.
The first build requires network access to obtain Gradle and Maven dependencies;
automated tests need no real database or SoCLaaS credentials. Running the
application now requires the database variables in [Supabase setup](SupabaseSetup.md).

```powershell
.\gradlew.bat test bootJar
java -jar build/libs/facilityflow-ai-0.1.0-SNAPSHOT.jar --spring.profiles.active=database-check
```

On macOS/Linux, use `sh ./gradlew test bootJar`. The current non-web scaffold
connects for the explicit smoke check and exits; there is no page to open yet.
Set the documented database environment before running the JAR. Test reports are under
`build/reports/tests/test/`. The source configuration is
`src/main/resources/application.yml`; do not edit generated `bin/` or `build/`
copies. Builds use Java 25, Spring Boot 4.1.1, Gradle 9.1.0 and JUnit 5.13.4;
see [ADR-003](../workflow/decisions/ADR-003-build-baseline.md).

This guide will describe the released architecture, data model, security model,
SoC LLM integration, specification-driven workflow, multi-agent workflow,
testing, deployment, operations, and acknowledged reuse. It must match the
latest release rather than the intended design.

## Project overview

FacilityFlow AI, CS3227 MP3. Planning/scaffolding only; see README and AGENTS.md.

## Repository structure

src/: future code/tests; workflow/: specs/process; docs/: guides/planning;
logs/: summaries; .github/: PR-Agent/contribution template. The Gradle build
packages the minimal application; feature modules are pending.

## Architecture

Existing plan: Java 25/Spring Boot modular monolith, Gradle, Thymeleaf/HTMX,
Spring Security, PostgreSQL and Docker. Detailed design and the pinned compatible
Spring Boot/Gradle versions are governed by
../workflow/specs/TECHNICAL-SPEC.md.

## Data model

Supabase is the user-selected PostgreSQL provider. JDBC uses environment-backed
credentials, certificate verification and a two-connection development pool;
SQL initialization is disabled. The `database-check` profile performs a read-only
connectivity probe. Part 2b now supplies versioned identity/audit/session storage;
the `database-migrate` profile explicitly enables Flyway. No Supabase Auth
integration was added. Request data and live session behavior remain pending.

The accepted entities, relationships, transaction boundaries, invariants and
concurrency approach are defined in `../workflow/specs/TECHNICAL-SPEC.md`.

## Role model

Requester owner: `yooplo`; Technician owner: `ngkhengyang`; Facilities Manager
owner: `yu-sutong`. Members may contribute across slices, but each owner remains
accountable for completing their role features. Detailed permissions and
Account lifecycle rules are defined in the shared and role specifications.

## Security design

Deterministic server-side auth, authorization, validation, transitions and
writes are required. Detailed controls and implementation evidence: TBD.

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
