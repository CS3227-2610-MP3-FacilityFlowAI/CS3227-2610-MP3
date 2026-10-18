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
implemented locally together with the non-mutating Triage Assistant review
surface; remaining role workflows are pending. See README and AGENTS.md.

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

## Facilities Manager workflow

`ManagerViews` is the small web-facing use-case interface. `ManagerService`
opens the transaction, rejects non-Managers and asks `ManagerRepository` to
recheck the persisted active Manager before reading or writing.
`JdbcManagerRepository` owns parameterized PostgreSQL access; controllers issue
no SQL.

REQ-MGR-001–003/010–011 provide the operational counts, deterministic
all-request queue, literal combined filters, active Technician workloads,
read-only audit filters and Manager recording on behalf of an active Requester.
The create transaction locks/rechecks the Manager, Requester and Category,
inserts an OPEN Request with distinct owner/Recording Actor, and appends one
`REQUEST_RECORDED_ON_BEHALF` event. Failure rolls back both. Inactive Requesters
are excluded from choices and rejected again inside the transaction.

The audit projection left-joins Request targets. Non-Request events retain a
null Request display ID; they are never mapped to a sentinel value. Request,
actor, action and inclusive Singapore date filters are combined with AND and
return at most 200 newest events.

REQ-MGR-004–012 are implemented as named `ManagerService` operations. Each write
locks and re-reads the Request, compares the submitted optimistic version,
validates the allowed source state and active Technician/category, updates the
Request and appends its Audit Event in one transaction. Returning or reopening
clears only the current completion projection; immutable completion attempts and
Work Logs remain available on the Manager detail page. Injected audit failures
are covered by rollback tests.

`ManagerAdminService` and `JdbcManagerAdminRepository` implement REQ-MGR-013–019.
Deactivation and password reset invalidate the target's existing sessions by
advancing `session_version`; eligible role changes retain the session so the
persisted-account filter applies the new role on the next request. Self,
last-active-Manager and active-assignment guards are deterministic. Category removal locks the catalogue item, requires a
different active replacement, migrates Requests with per-Request audit evidence
and deactivates the source atomically. Active Technician-skill selections can be
replaced while links to inactive historical tags are retained.

`ManagerTriagePageController` adds a server-rendered, no-store review surface on
`/manager/requests/{displayId}/triage-assistant`. It calls the merged gateway's
`ManagerTriageService`, renders safe failure/retry guidance, requires human
review, leaves suggested skill tags unselected, and shows the deterministic
Technician order. A successful OPEN-request review carries only the fresh
Request version and active catalogue/Technician options; the final POST uses
`ManagerService.saveTriage`, which repeats ordinary assignment locks and
validation before any mutation. AI code remains non-mutating.

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

The shared gateway now includes startup-safe configuration, HTTPS identity-only
transport, generation-aware readiness recovery, bounded decoding,
admission/lease coordination, telemetry, bounded retry, a single end-to-end
Technician deadline, strict typed Work Plan/Smart Report/Triage provider
contracts, deterministic Manager candidate ranking and server-side output
disclosure checks. The provider core remains advisory and mutation-free. Only
the Manager slice currently adds a role-facing review page over its service/API;
Requester Smart Report and Technician Work Plan remain API/service seams until
their owners add workflow UI. Compressed responses remain deferred by the
authoritative integration specification.

`AI_ENABLED` defaults to `false`. When enabled, the application binds all
deployment values as raw strings and resolves them to disabled, unavailable or
validated settings without exposing the API key or allowing malformed values to
prevent the manual application from starting. The configured base must resolve
to the official HTTPS SoCLaaS `/v1` base. Timeout, output, concurrency and rate
settings may tighten but not exceed 30 seconds, 800 output tokens, five
concurrent in-flight calls application-wide, five Account calls per minute and
30 per hour.

Copy `.env.example` into the developer's approved local secret mechanism; never
commit a populated file. AI requires `AI_ENABLED=true`, a real
`SOCLAAS_API_KEY`, and a configured `SOCLAAS_MODEL`. The remaining variables are
`SOCLAAS_BASE_URL`, `AI_TIMEOUT_SECONDS`, `AI_MAX_OUTPUT_TOKENS`,
`AI_MAX_IN_FLIGHT`, `AI_RATE_PER_MINUTE` and `AI_RATE_PER_HOUR`. Invalid,
placeholder or over-limit values make AI unavailable. No local test calls a live
SoCLaaS provider. A manual 17 October 2026 smoke check verified model discovery
and one minimal completion using the developer's external secret environment;
no credential or raw response was recorded in the repository.

## AI security

Gateway failures include a stable `code` and readable `message`, for example:

```json
{"code":"AI_INVALID_OUTPUT","message":"The AI response had an invalid format or failed output validation."}
```

Inspect the server console for `SoCLaaS gateway` entries. Failure entries name
the feature and code; provider diagnostics additionally identify the stage and
safe reason, for example `stage=READINESS reason=MODEL_NOT_FOUND`,
`stage=READINESS reason=INVALID_ENVELOPE`,
`stage=COMPLETION reason=HTTP_UNAUTHORIZED`, or
`stage=WORK_PLAN_OUTPUT reason=INVALID_OUTPUT`. Authentication/access rejection
maps to `AI_CONFIGURATION_ERROR`; invalid format or output validation maps to
`AI_INVALID_OUTPUT`; timeouts map to `AI_TIMEOUT`. For `AI_UNAVAILABLE`,
check the startup disabled/configuration log and readiness diagnostics.
Logs omit credentials, raw prompts/responses, context and exception messages.

Prompts/context/output are untrusted. `AiAccessGuard` implements the
authorization-before-context seam required by AI-SEC-001 and is verified to
construct no protected context and make no model call after denial. Configuration
validation and redaction are implemented and locally verified; typed schemas,
runtime limits, transport/readiness, retry, telemetry and provider disclosure
checks are covered by focused tests. Manager role orchestration and its review
surface are integrated locally; the approved Work Plan multilingual safety
corpus, hosted application verification and final Security Reviewer/teammate
verification remain pending. See
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
GitHub Actions now defines tests, secret/dependency checks, the Java 25
container build, optional Vercel Preview deployment, staged Production candidate,
smoke checks, protected no-rebuild promotion and manual rollback. Deployment is
disabled until the team configures separated Vercel values, GitHub secrets and a
required human reviewer. Follow [Deployment](Deployment.md). Hosted deployment,
monitoring, backup and restore evidence remain pending and must not be claimed
from workflow configuration alone. The pull-request/master verification job
validates the Gradle wrapper, runs `clean check bootJar`, executes dependency-free
`src/test/js/*.test.cjs` checks when present, scans the packaged application and
committed history, and uploads test, coverage and JAR artifacts.

## MP2 reuse

The team reported lecturer confirmation that MP2 reuse is permitted when MP3 is
a deployed web application with a production database and documented reuse.
The source baseline commit is verified in `../workflow/REUSE.md`. Measure code,
tests, and documentation separately and acknowledge every source before merge.

## Acknowledgements

Team: ../AGENTS.md. AI evidence: ../logs/. Reused material: reuse ledger.
Other source/dependency acknowledgements: TBD.
