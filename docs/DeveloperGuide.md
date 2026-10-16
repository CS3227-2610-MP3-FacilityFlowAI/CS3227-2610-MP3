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

FacilityFlow AI, CS3227 MP3. Database and authentication foundations integrated;
Requester manual workflow, dashboard and discovery are implemented locally; the remaining role
business workflows are pending. See README and AGENTS.md.

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

## Requester first manual slice (step 2)

REQ-RQ-002/004/005/009–011: `RequesterRequests` exposes bounded use cases and page
records; `RequesterRequestService` supplies the transaction boundary and
`RequesterRequestRepository` isolates persistence. Controllers accept an immutable
five-field `RequestDraft` plus a submission ID. Actor/owner/status/priority/IDs
and timestamps are never bound from client authority fields.

Every operation rechecks persisted active state, REQUESTER role and session
version in its transaction. Reads hold a shared account lock; creation holds an
exclusive account lock so duplicate submissions from the same account serialize
across server instances. The selected active category is locked for the short
create transaction. Request creation, its audit event and durable submission ID
commit together. Validation, stale account, duplicate command, audit/storage
failure and display-number exhaustion leave no partial business record.

Queries scope by owner before selecting detail/history. History projects only
approved lifecycle/edit labels, public transition reasons and Requester Updates;
it never selects Work Logs or returns the audit details object. Returned page
records omit internal Request UUIDs, credentials, assignments and completion metadata,
following retained MP2 Requester detail behavior. Thymeleaf encodes user text.
History action names are the `REQUEST_CREATED/EDITED/ASSIGNED/REASSIGNED/STARTED/
COMPLETED/CLOSED/CANCELLED/RETURNED/REOPENED` variants enumerated in the repository;
public transition reasons use `details.reason`. Other role services must use
this shared history contract or update the projection with matching tests.

Apply **V5** using the existing explicit migration profile before web startup.
It adds an append-only submission table and read-only `schema_version` view.
`SchemaReadinessCheck` requires successful versions 1–5; missing/failed/future
versions stop startup with safe guidance. Runtime still cannot read or write the
full Flyway history or perform DDL. Flyway checksum validation remains part of
the explicit migration process; startup checks version compatibility only.

`RequesterRequestIntegrationTest` starts a loopback app with genuine disposable
PostgreSQL and the restricted app role. Existing H2 tests remain authentication-
only with a clearly marked readiness fixture. Run `gradlew.bat test bootJar` and
`node src/test/js/request-form.test.cjs`; the Node check uses only standard APIs.
No new application dependency was added. Browser/hosted/performance verification
and human review remain pending.

## Requester eligible mutations (step 3)

REQ-RQ-006–011: the existing use-case/repository interfaces now expose bounded
edit, cancellation and append-update operations. Each transaction rechecks the
active Requester/session version, locks only an owned Request, validates its
persisted state and compares the submitted non-negative version. Edit/cancel are
OPEN-only; updates reject CLOSED/CANCELLED. A missing/foreign Request yields the
same unavailable result. The SQL writes also assert expected version/state.

Every successful mutation increments Request.version, including an append update.
Repeated or concurrent submissions with the same version reject the second
operation. Request fields/state/update insertion and audit append share one
transaction; audit failure restores the prior row, version and history. Edit
rechecks/locks the selected active category. Public cancellation reasons are
stored in `details.reason`; update text and report free text are never copied
into audit details. Updated timestamps use PostgreSQL clock_timestamp after lock
acquisition. No migration or application dependency was added in this step.

The create template is reused for edit with a version field. A shared action
page handles cancellation and updates. Cancellation requires a server-validated
reason, checkbox and explicit cancel decision; missing/implicit decisions keep
the report. Keep is the first submit action. The shared pending-submit script
leaves named decision buttons enabled so their values remain in the browser's
payload, while rejecting a second pending submit. Node checks cover that case.

Centralized mutation error handling preserves submitted values and their original
version; it never substitutes a fresh version behind stale user input. Reloading
is explicit. Tests cover all states, replay, foreign/wrong-role/CSRF denial,
post-load assignment/category changes, concurrent edits/cancel-update races,
all-mutation audit rollback/retry and inactive/invalidated direct-service access.

## Requester dashboard and discovery (step 4)

REQ-RQ-001/003/004/009 and REQ-SH-008/009: `RequestFilters` binds only five
immutable filter inputs, normalizes whitespace and validates status, category
UUID, strict dates and date order before queries. Invalid or unknown category
filters return HTTP 422 with retained values and no unfiltered fallback.

The service derives the owner from the revalidated account. Every count/list
query constrains that owner; the search OR group remains inside that predicate.
Bound `position` expressions implement literal case-insensitive substrings over
display ID, title and location. All filters combine with AND. Inclusive Singapore
dates become UTC bounds `created_at >= start` and `created_at < next day start`.
List and five-recent queries share creation-descending/display-ID-ascending order.
The dashboard fills absent states with zero. Filter categories include inactive
history options; creation/edit choices remain active-only. No migration or
dependency was added.

PostgreSQL integration tests cover ownership, all six counts, five recent reports,
literal punctuation, combined filters, local midnight boundaries, reset/empty
states and escaped retained errors. The H2 authentication suite uses a primary
`RequesterRequests` dashboard stub registered only by its test initializer,
because H2 cannot execute PostgreSQL `FOR SHARE`. Its security chain, persisted
accounts, JDBC sessions and password/audit services remain real; PostgreSQL
workspace tests exercise the actual authorization and locking.

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

## Identity operations (Requester step 1)

REQ-SH-002/004/006/014: `/account/password` accepts POST with CSRF for any
authenticated role. `PasswordChangeService` obtains the actor from the server
session, reloads active state/role/session version, verifies the current password,
validates the new password by Unicode code points and encodes it before opening
the transaction. `JdbcIdentityOperations` conditionally updates the expected hash,
role and session version, increments the account row version and appends
`PASSWORD_CHANGED` atomically. A changed account rejects the write; audit failure
rolls it back. Passwords are never returned in the page model. Own-password change
leaves session versions and existing sessions intact, matching retained MP2 behavior.

`RoleLoginSuccessHandler` records `LOGIN_SUCCESS` before redirecting. Audit failure
clears authentication and invalidates the newly established session, returning the
generic login failure. Neither audit action records password material.

Manager integration contract (REQ-SH-004, REQ-MGR-013–015): Manager operations
remain with the Manager owner. Deactivation/reset must atomically mutate the
account, increment `session_version`, remove affected JDBC sessions and append
their audit event. The existing request filter also rejects stale session versions.
Eligible role changes retain the current session and the next protected request
reloads the persisted role. Manager self-change restrictions and active Technician
assignment guards must be enforced in those use cases; this password endpoint
accepts no target account or role. Those Manager operations remain unimplemented.

The password interaction is sourced from MP2 commit
`9819b857d880134c1fc1ffef8a8bcd5d6e536f43`, `AuthenticationService.changePassword`
and `ApplicationRouter`'s password page, under the spec index's source precedence.
Spring reference: [form login success processing](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/passwords/form.html).

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
workflows, backups and rollback remain unimplemented.

## MP2 reuse

The team reported lecturer confirmation that MP2 reuse is permitted when MP3 is
a deployed web application with a production database and documented reuse.
The source baseline commit is verified in `../workflow/REUSE.md`. Measure code,
tests, and documentation separately and acknowledge every source before merge.

## Acknowledgements

Team: ../AGENTS.md. AI evidence: ../logs/. Reused material: reuse ledger.
Other source/dependency acknowledgements: TBD.
