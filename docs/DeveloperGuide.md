# Developer Guide

Status: Shared database schema and explicit initialization supplied; V1 application
and permissions evidenced by user screenshots. Hosted V2/V3/demo results are
reported matching by the user; independent hosted verification remains pending.
Technician assigned-work reads, start/log/completion commands and Work Plan browser controls are implemented
in source as described below.
Follow [DatabaseCompletion](DatabaseCompletion.md).

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

FacilityFlow AI, CS3227 MP3. Database/authentication foundations and Technician
assigned-work reads, start/log and completion commands are integrated in source;
other role manual workflows remain pending. See README and AGENTS.md.

## Repository structure

src/: application code/tests; workflow/: specs/process; docs/: guides/planning;
logs/: summaries; .github/: PR-Agent/contribution template. The Gradle build
packages the application and Technician read/command modules; other role manual
modules remain pending.

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

## Role model

### Technician assigned-work reads (T1)

`TechnicianQueryService` supplies queue/detail use cases through immutable nested
`TechnicianReadModels`. Its JDBC repository owns a read-only REQUIRES_NEW,
REPEATABLE_READ transaction and a callback-scoped, thread-confined ReadSession.
Persisted active state, TECHNICIAN role and exact session version are checked
before Request reads, including direct use-case calls. Session capabilities are
closed on every callback exit. Query SQL repeats current-assignment scope for
Request history; original historical authorship is retained.

Queue counts are unfiltered while rows use combined filters and parameterized
literal ILIKE search. Priority/urgency rank expressions precede assignment time
with NULLS LAST and display ID. Retained terminal assignments remain readable.
Invalid filters, inaccessible detail and operational failures map to safe
400/404/503 views; failures never broaden results. New read paths do not write
business or Audit Events.

`TechnicianQueueController` handles `/technician/requests` and detail routes.
The existing `DashboardController` reuses its default-filter workspace renderer
for `/technician`; other role routes remain unchanged. Templates use escaped
text, UTC Instants rendered in Asia/Singapore, and external `/css/technician.css`
under the existing CSP. No write/AI browser controls are part of T1.

See [contracts](../workflow/technician/CONTRACTS.md),
[acceptance map](../workflow/technician/ACCEPTANCE-MAP.md) and the
[T1 report](../logs/technician/T1-REPORT.md) for executed evidence. SQL/transaction
tests use disposable embedded PostgreSQL with the runtime database role;
the existing H2 authentication suite uses minimal maintenance read tables and
synthetic rows for authenticated landing/detail rendering; it does not prove
PostgreSQL SQL semantics. JUnit 5 tests construct Spring contexts manually where needed because
the pinned Spring 7 JUnit extension requires JUnit 6.

### Technician work commands (T2)

`TechnicianCommandService` exposes only start-work and append-Work-Log commands.
It captures the server-side Technician identity and validates display ID,
nonnegative expected Request version, trimmed note code points and whole minutes.
`JdbcTechnicianCommandRepository` independently validates its direct inputs and
uses one READ_COMMITTED, REQUIRES_NEW transaction per command. Account FOR SHARE
locks protect persisted active/role/exact session-version validation; the scoped
Request FOR UPDATE lock serializes assignment/state/version checks and writes.
Every success increments Request.version once, making in-flight Work Plans stale.

Start changes ASSIGNED to IN_PROGRESS; Work Log append requires IN_PROGRESS and
preserves original author/UTC time. Request mutation, optional Work Log INSERT
and one safe Audit Event commit together, including rollback when audit fails.
Audit details contain only allowlisted status/version or minutes/version, never
free-text notes. Existing runtime grants forbid history/audit UPDATE and DELETE.
No migration or additional dependency is introduced.

`TechnicianCommandController` handles POST `/{displayId}/start` and
`/{displayId}/work-logs` under `/technician/requests`, with existing role/CSRF
protection. Success uses a fixed same-origin 303 detail redirect. Validation,
conflict, inaccessible-object and operational failures map to safe 400/409/404/503;
only the assigned query service can reload fresh details after a failure.
Submitted drafts remain escaped and visible even if the Request is unavailable
or no longer accepts a log. Forms use persisted versions; conflicts need explicit
review/resubmission. External JavaScript blocks duplicate pending submissions
and restores buttons on page return without weakening CSP.

See the [T2 contract](../logs/technician/T2-CONTRACT.md) and
[T2 report](../logs/technician/T2-REPORT.md) for ownership, cases and execution.
PostgreSQL command tests use a class-scoped disposable server with isolated
per-test data and the restricted runtime role. Authenticated MVC tests use a
command-service double plus real assigned-query/Thymeleaf rendering; those H2
checks prove web behavior, while PostgreSQL tests prove transaction semantics.
The standalone JavaScript event check models pending-submit/page-return behavior;
it does not replace supported-browser visual/keyboard acceptance.

### Technician Work Plan browser integration (T4)

The existing POST `/technician/requests/{displayId}/work-plan` returns either
`{plan: ...}` or `{code, message}` with `Cache-Control: no-store`. The detail page
shows its separate CSRF-protected form only for ASSIGNED/IN_PROGRESS. External
`technician-work-plan.js` validates the entire seven-field result before rendering
plain text, prioritizes safety/stop guidance and maps safe errors to manual
fallback. It neither fills nor disables manual forms. Abort/generation checks
discard late results on manual submission and page lifecycle changes. No plan is
stored in browser persistence or server session.

`TechnicianWorkPlanRepository` requires a captured `CurrentAccountSnapshot` for
both context loading and final eligibility checks. The JDBC implementation
requires an active persisted TECHNICIAN and exact nonnegative session version
before loading assigned context, in a short read-only repeatable-read snapshot.
No transaction spans provider execution. Final checks require the same account,
session, assignment, original status and Request version; changed access discards
the whole result and cannot produce successful telemetry. Existing provider
allowlisting, schema/safety/disclosure rejection, admission and deadline behavior
remain in force. No new business mutation or migration is involved.

T4 evidence separates actual MVC/security/Thymeleaf tests (service double),
asynchronous orchestration tests, real isolated PostgreSQL repository tests and
actual-script deterministic DOM/fetch tests. Existing provider adversarial tests
cover stored injection and unsafe advice; no live provider was used. See
`logs/technician/T4-REPORT.md` for executed results and remaining human/browser gates.

### Technician completion (T3)

`completeWork` extends the existing command service/repository without a generic
status setter. The repository reuses persisted account and scoped Request locks,
then checks IN_PROGRESS, exact expected version and at least one existing Work
Log (including prior authors). Service and repository enforce a trimmed
10–2,000-code-point resolution. Missing logs produce WORK_LOG_REQUIRED, safe400;
existing invalid-input/access/conflict/operational semantics remain.

Completion retains assignment, increments version once, records server author
and one database completion instant in current metadata and a new immutable
completion-history row, and appends one safe TECHNICIAN_WORK_COMPLETED audit
(statuses/version only). Request/history/audit commit together or roll back.
Prior history remains on later return/recompletion. No auto-close or schema or
dependency change is introduced.

POST `/technician/requests/{displayId}/complete` uses existing CSRF/role checks,
versioned form, pending-submit script and fixed303 detail redirect. The controller
preserves escaped unsaved resolutions even when a fresh scoped read is no longer
available. COMPLETED shows persisted metadata and no work mutation controls.
See [T3 contract](../logs/technician/T3-CONTRACT.md) and
[T3 report](../logs/technician/T3-REPORT.md) for completion tests, fault-injection
rollback, concurrent completion/reassignment and per-agent evidence. Real
PostgreSQL proves writes; authenticated H2 MVC uses the existing command double
and actual query/template path. Browser/release acceptance remains separate.

### Slice ownership

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
the Technician Work Plan currently has a role-facing service/controller seam;
Requester Smart Report and Manager Triage orchestration remain pending their
role-owner and teammate approval evidence. Compressed responses remain deferred
by the authoritative integration specification.

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
SoCLaaS provider or validates a hosted model.

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
checks are covered by focused tests. Requester/Manager role orchestration, the
approved Work Plan multilingual safety corpus, hosted-provider smoke testing
and final Security Reviewer/teammate verification remain pending. See
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
workflows, backups and rollback remain unimplemented.

## MP2 reuse

The team reported lecturer confirmation that MP2 reuse is permitted when MP3 is
a deployed web application with a production database and documented reuse.
The source baseline commit is verified in `../workflow/REUSE.md`. Measure code,
tests, and documentation separately and acknowledge every source before merge.

## Acknowledgements

Team: ../AGENTS.md. AI evidence: ../logs/. Reused material: reuse ledger.
Other source/dependency acknowledgements: TBD.
