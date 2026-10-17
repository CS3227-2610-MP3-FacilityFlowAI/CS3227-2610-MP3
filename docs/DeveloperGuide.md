# Developer Guide

Status: Shared database schema and explicit initialization supplied; V1 application
and permissions evidenced by user screenshots. Hosted V2/V3/demo results are
reported matching by the user; independent hosted queries and product features
remain pending. Follow [DatabaseCompletion](DatabaseCompletion.md).

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
java -jar build/libs/facilityflow-ai-0.1.0-SNAPSHOT.jar --spring.main.web-application-type=none --spring.profiles.active=database-check
```

On macOS/Linux, use `sh ./gradlew test bootJar`. Database commands run
without an HTTP server: add `--spring.main.web-application-type=none` to the
`database-check`, `database-migrate` and `database-initialize` Java commands.
Ordinary startup serves `/login` and role workspaces. For local HTTP only,
set `SESSION_COOKIE_SECURE=false`; retain secure cookies for HTTPS.
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

FacilityFlow AI, CS3227 MP3. Database and authentication foundations are
integrated. The first Manager overview/queue/audit/on-behalf-create slice is
implemented locally; remaining role workflows are pending. See README and AGENTS.md.

## Repository structure

src/: application code/tests; workflow/: specs/process; docs/: guides/planning;
logs/: summaries; .github/: PR-Agent/contribution template. The Gradle build
packages the minimal application; feature modules are pending.

## Architecture

The implemented foundation pins Java 25, Gradle 9.1.0 and Spring Boot 4.1.1.
Spring MVC/Thymeleaf serves pages, Spring Security controls authentication,
Spring Session JDBC persists browser sessions, Flyway owns schema changes through the explicit migration profile, and
JDBC repositories isolate PostgreSQL access. The approved wider modular-monolith
design remains in `../workflow/specs/TECHNICAL-SPEC.md`.

## Data model

Supabase is the user-selected PostgreSQL provider. JDBC uses environment-backed
credentials, certificate verification and a two-connection development pool;
SQL initialization is disabled. The `database-check` profile performs a read-only
connectivity probe. Part 2b now supplies versioned identity/audit/session storage;
the `database-migrate` profile explicitly enables Flyway. No Supabase Auth
integration was added. Authentication and JDBC sessions now use the private schema.

V2/V3 now supply the remaining maintenance/catalogue/history/skills/AI/limit tables.
The `database-initialize` profile performs explicit first setup using app credentials,
with DEMO_DATA_ENABLED false by default. Development demo and production Manager
bootstrap are transactional, audited and idempotent. Existing Accounts skip seed;
pre-existing business/catalogue changes prevent fresh initialization.

Password storage is Boot-managed Spring Security crypto 7.1.1, PBKDF2 v5.8 defaults,
16-byte random salt, 310,000 SHA-256 iterations and 256-bit output. Stored hashes
carry `{pbkdf2@SpringSecurity_v5_8}` and are verified in tests with Spring's
delegating encoder. Login accepts this format through a delegating encoder; new passwords use
`{argon2}` and the pinned parameters below. Business lifecycle and AI limits
remain pending application integration; migrations alone do not implement those controls.

The accepted entities, relationships, transaction boundaries, invariants and
concurrency approach are defined in `../workflow/specs/TECHNICAL-SPEC.md`.

## Facilities Manager first slice

`ManagerViews` is the small web-facing use-case interface. `ManagerService`
opens the transaction, rejects non-Managers and asks `ManagerRepository` to
recheck the persisted active Manager before reading or writing.
`JdbcManagerRepository` owns parameterized PostgreSQL access; controllers issue
no SQL.

REQ-MGR-001–003/010–011 currently provide the operational counts, deterministic
all-request queue, literal combined filters, active Technician workloads,
read-only audit filters and Manager recording on behalf of an active Requester.
The create transaction locks/rechecks the Manager, Requester and Category,
inserts an OPEN Request with distinct owner/Recording Actor, and appends one
`REQUEST_RECORDED_ON_BEHALF` event. Failure rolls back both. Inactive Requesters
are excluded from choices and rejected again inside the transaction.

The audit projection left-joins Request targets. Non-Request events retain a
null Request display ID; they are never mapped to a sentinel value. Request,
actor, action and inclusive Singapore date filters are combined with AND and
return at most 200 newest events. Manager lifecycle actions, details, account and
catalogue administration, Triage Assistant, browser verification and hosted
verification remain pending.

## Role model

Requester owner: `yooplo`; Technician owner: `ngkhengyang`; Facilities Manager
owner: `yu-sutong`. Members may contribute across slices, but each owner remains
accountable for completing their role features. Detailed permissions and
Account lifecycle rules are defined in the shared and role specifications.

## Security design

Spring Security form login uses one generic invalid-credentials result for an
unknown username, incorrect password or inactive account. Argon2id parameters
are configuration-pinned; existing prefixed PBKDF2 hashes remain valid. Every authenticated request reloads active state,
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

Run `./gradlew test bootJar`. Storage and initialization tests use disposable
PostgreSQL, including restricted-role repository reads and timestamps. Web tests
use an isolated H2 fixture under `src/test/resources/db/authentication`, separate
from applied production migrations, and cover seeded PBKDF2 login as well as
Argon2, session invalidation, role denial and CSRF. Browser/hosted checks remain
required before release; local tests do not connect to Supabase.

Follow the database guides for setup. Ordinary startup does not migrate or seed;
use the app role for `./gradlew bootRun`, then open `/login`.

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
workflows, backups and rollback remain unimplemented. The new pull-request/master
CI workflow validates the Gradle wrapper, runs `clean check bootJar`, executes
dependency-free `src/test/js/*.test.cjs` checks when present, and uploads test,
coverage and JAR artifacts. This is CI evidence only: Preview deployment,
candidate smoke testing, human promotion, rollback and environment separation
remain unimplemented until the Vercel spike confirms the approved flow.

## MP2 reuse

The team reported lecturer confirmation that MP2 reuse is permitted when MP3 is
a deployed web application with a production database and documented reuse.
The source baseline commit is verified in `../workflow/REUSE.md`. Measure code,
tests, and documentation separately and acknowledge every source before merge.

## Acknowledgements

Team: ../AGENTS.md. AI evidence: ../logs/. Reused material: reuse ledger.
Other source/dependency acknowledgements: TBD.
