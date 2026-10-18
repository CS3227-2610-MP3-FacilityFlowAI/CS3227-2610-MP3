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

FacilityFlow AI, CS3227 MP3. Database and authentication foundations, Requester
manual workflow and Smart Report review, and Facilities Manager lifecycle,
administration and Triage Assistant review are implemented locally. Technician
workflow UI and hosted release verification remain pending. See README and AGENTS.md.

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

Apply **V5 and V6** using the existing explicit migration profile before web startup.
It adds an append-only submission table and read-only `schema_version` view.
`SchemaReadinessCheck` requires successful versions 1-6; missing/failed/future
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

### Shared gateway from master

The shared gateway now includes startup-safe configuration, HTTPS identity-only
transport, generation-aware readiness recovery, bounded decoding,
admission/lease coordination, telemetry, bounded retry, a single end-to-end
Technician deadline, strict typed Work Plan/Smart Report/Triage provider
contracts, deterministic Manager candidate ranking and server-side output
disclosure checks. The provider core remains advisory and mutation-free. Technician Work Plan, Requester Smart Report and Manager Triage now have
role-facing service/controller seams; owner/teammate release approval remains
pending. The Manager slice also supplies a Triage Assistant review page.
The Requester review UI continues to use its separate
`/requester/requests/assistant` flow documented below. Compressed responses remain deferred
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
SoCLaaS provider. A manual 17 October 2026 smoke check verified model discovery
and one minimal completion using the developer's external secret environment;
no credential or raw response was recorded in the repository.

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

### Smart Report service (Requester step 5)

`ai/SmartReportService` calls the role-owned read repository, `AiGateway` and
`AiOperations` persistence seams. POST `/requester/requests/assistant` accepts
`SmartReportInput`; POST `/requester/requests/{displayId}/assistant` also requires
the ordinary Request version. Existing role filters and CSRF protect both. They
return no-store JSON with a typed suggestion or stable failure category and safe
guidance. The step 6 review UI is described below. Service code
never calls a Request mutation or business-audit operation.

The input permits incomplete current fields within their normal maxima. Actor,
object/version checks, input bounds and database admission precede category
loading/provider use. Context includes only current rough/form text, selected
category label/urgency and active category labels. No saved Request description,
Account/display/internal IDs, history or unrelated records enter the prompt.
Fixed field rules live in source-controlled `SmartReportSchema` template
`smart-report-v1`. Jackson 3 serializes one provenance-labelled data object;
untrusted strings never extend the fixed system instructions.

`ValidatedAiGateway` bounds aggregate system/data JSON to 12,000 UTF-16 units
(conservative for Unicode), output to 800 tokens and deadline to 30 seconds.
It has five worker slots without a waiting queue, cancels/ignores late responses,
and retries network/503 once or 429 once only for Retry-After up to two seconds
inside the original deadline. Other failures never retry. The strict parser
rejects duplicate/unknown fields, trailing JSON, wrong types, invalid enum/category,
empty optional strings, schema lengths and missing required structure. Plain-text
validation rejects active markup/links, unsafe control/format characters, identifiers,
prompt markers, known secrets and adapter-provided disclosure canaries. This
bounded detector is not proof against every semantic or obfuscated disclosure.

`JdbcAiOperations` uses the existing V3 tables: a locked per-Account rolling
timestamp array (five/minute, 30/hour) plus five global expiring slot leases
claimed with FOR UPDATE SKIP LOCKED. Versioned release cannot clear a newer
lease. Short transactions end before inference. Limits survive service instances;
each invocation reserves one quota unit, including its permitted retry. Leases
last the configured provider deadline plus 15 seconds for local processing.
Adapters must honor remaining deadlines/interruption; bounded workers also
prevent ignored cancellation from creating an unbounded local call queue.

Telemetry stores actor/correlation IDs, feature, configured model/template,
status/latency/retry and provider usage, retaining known counts even when a
freshness check discards output. Missing usage remains null. No prompt, provider
error body, free-text input/output or business Audit Event is recorded. A metadata
storage failure cannot return a successful suggestion.

Runtime `AiConfiguration` now installs `SoClaasTransport`; `FakeAiTransport`
exists only under tests. Missing/invalid configuration leaves AI unavailable.
Portal reconciliation and other role results remain pending with the gateway owner.
`AI_ENABLED` defaults false. `AI_TIMEOUT_SECONDS`, `AI_MAX_OUTPUT_TOKENS`,
`AI_MAX_IN_FLIGHT`, `AI_RATE_PER_MINUTE` and `AI_RATE_PER_HOUR` may reduce defaults;
values are clamped to 1 through their approved maximum. `SOCLAAS_API_KEY` is a
server-only bearer credential and forbidden output value. No dependency or
migration was added. Tests use synthetic values and no external provider.

### Review UI and SoCLaaS adapter (Requester step 6)

`requester/create.html` embeds an optional, separately labelled review section
outside the ordinary create/edit form. `smart-report.js` sends only current
allowlisted fields, rough text, expected edit version and CSRF to the same-origin
POST endpoint. It renders with textContent, never HTML/Markdown. Explicit per-field
buttons map active category labels to current form option IDs and urgency labels
to the ordinary enum. Accepted fields remain editable. There is no AI submit or
save operation, acceptance flag or result ID that can authorize a write.

While awaiting a response, manual fields remain usable. Changed input discards
late output; a field changed after review cannot be overwritten by its old
suggestion. Discard, ordinary submission and page navigation abort/ignore pending
browser results and remove transient suggestion nodes. No chat/localStorage or
other suggestion persistence. Browser cancellation is best effort; the server
invocation remains bounded by its deadline/quotas. A no-JavaScript manual path
remains available. Framework page/JSON escaping plus DOM textContent protect
display; no raw provider body is sent to the browser.

`SoClaasTransport` uses one JDK HttpClient, default certificate validation and no
redirects. Runtime accepts only the approved HTTPS SoCLaaS `/v1` origin; it never
sends credentials to configurable arbitrary hosts or an HTTP URL. The
adapter also rejects a model setting containing the configured key, preventing
that misconfiguration from reaching model metadata. The
package-private injected client/base constructor is for loopback contract tests,
with no runtime insecure flag. Configuration uses `AI_ENABLED=true`,
`SOCLAAS_BASE_URL` (default `https://soclaas-api.comp.nus.edu.sg/v1`), a server-only
`SOCLAAS_API_KEY` and `SOCLAAS_MODEL`. No model is hard-coded. Configure separate
non-production/production keys where supported, outside tracked files.

Startup and `available()` never contact the provider. After ordinary admission,
first use checks GET `/models` within two seconds and the invocation's remaining
deadline. One probe per process runs at a time; success caches five minutes,
failure cools down 60 seconds. Generation uses POST `/chat/completions` with model,
stream=false, max_tokens and exactly system/user messages. No tools, history or
response ID. Credentials stay in the Authorization header. Generation and probe
share the original deadline; future cancellation closes the HTTP exchange.

Response bodies are capped at 64 KiB with a typed overflow error so oversized or
malformed output never becomes a network retry. The adapter requires exactly one
completed assistant-text choice and rejects tool/refusal/truncated responses;
the existing strict Smart Report parser then validates content. Numeric non-negative
usage is extracted without estimating missing values. Status/Retry-After map into
the existing one-retry gateway policy; redirects never forward bearer headers.
No prompt/output/provider-error body is logged or persisted.

`SoClaasTransportTest` exercises the real HTTP adapter against a loopback provider.
PostgreSQL integration tests cover rendered create/edit routes and explicit
ordinary submission after review, including stale state and forged AI flags.
Run `node src/test/js/smart-report.test.cjs` plus the existing request-form self-
check for explicit acceptance/discard/stale/cancel/error/escaping behavior.
These DOM checks do not replace real-browser accessibility/viewport verification.
The user deferred the non-production live smoke check; no external model, token,
quality or readiness evidence is claimed. JDK reference:
[HttpClient](https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpClient.html).

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

Requester browser/load verification is opt-in:

```powershell
.\gradlew.bat requesterVerification
# Diagnostic override only; does not change application configuration:
.\gradlew.bat requesterVerification -PrequesterPoolSize=5
```

This needs Node with built-in fetch/WebSocket (verified with 24.19.0) and desktop
Chrome. Set `CHROME_PATH` if Chrome is outside the usual Windows installation
paths. The runner creates its own ignored profile and synthetic accounts in
disposable PostgreSQL; it never uses a personal profile or hosted database.
It exercises real HTTP/browser manual and fake-AI workflows, three desktop
viewports and 50 distinct authenticated users. Normal `test` excludes the tagged
browser task and needs neither Chrome nor Node. Results/screenshots are under
`build/verification/requester/`; cookie/CSRF values are not written to reports.
The versioned `src/test/resources/ai/smart-report-evaluation-v1.json` corpus is
deterministic fake-model safety evidence; it does not measure live usefulness.
The restart test retains saved requests/JDBC sessions across a graceful app
restart, not a database crash or restore. See the
[step 7 evidence](../logs/2026-10-16-requester-step-7-verification.md): browser
checks passed, but both local pool configurations missed the 1,000 ms load gate.
Timings include loopback networking; server-only and deployed performance,
latest-stable Chrome confirmation, manual accessibility and cross-role
lifecycle verification remain required. The task retains its failing gate.

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

## Migration ordering after the 18 October master merge

Master's `V5__ai_gateway_coordination.sql` stays V5. The Requester submission and
read-only schema-version migration moves to `V6__request_submission_and_readiness.sql`
without SQL content changes. Web readiness requires successful versions 1-6.
No hosted migration was executed during this merge. The user is unsure whether
the older Requester V5 was applied to shared Supabase; check privately before any
upgrade using the read-only query:

```sql
SELECT version, script, success
FROM facilityflow.flyway_schema_history
WHERE version IS NOT NULL ORDER BY installed_rank;
```

If V5 names `V5__request_submission_and_readiness.sql`, stop and coordinate a
migration-history reconciliation with the persistence owner. Do not run Flyway
repair, baseline, clean, or alter applied history to bypass validation. The merged
source sequence must not be applied blindly to that database. Disposable local
test databases use the merged sequence and do not establish hosted compatibility.

18 October history confirmation: the user's Supabase SQL Editor screenshot shows
successful versions1-5 with `V5__ai_gateway_coordination.sql`. The earlier V5
identity uncertainty above is resolved. Following credential correction, the
validated Flyway migration profile applied Requester V6 to development Supabase
on 18 October. Read-only postchecks confirmed successful V1-V6 history and app-role
visibility of `request_submission` and `schema_version`. No repair or reseeding
was performed. See [verification evidence](../logs/2026-10-18-supabase-history-and-full-reverification.md).
