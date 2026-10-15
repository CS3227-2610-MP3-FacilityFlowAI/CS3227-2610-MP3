# Technical Specification

Status: approved v0.1, 12 October 2026. Owner: Architect with shared owners in
AGENTS.md. Required role-owner and teammate approval has been confirmed. This
design records the accepted Vercel direction and is the implementation baseline.

## Stack and structure

- Java 25 and committed Gradle wrapper.
- Latest non-preview Spring Boot release at scaffold time that officially
  supports Java 25; pin the exact version in the build and decision record.
- Spring MVC, Thymeleaf and HTMX for server-rendered pages.
- Spring Security and Spring Session JDBC.
- PostgreSQL with Flyway versioned migrations and parameterized persistence.
- Jackson for strict AI JSON parsing; JUnit 5, Spring test facilities and an
  isolated PostgreSQL-compatible integration-test database.
- Dockerfile for local/CI and Dockerfile.vercel for Vercel deployment.

## Module map

The application is a modular monolith. Callers use each module's small use-case
interface; controllers never issue SQL or implement authorization/lifecycle rules.

| Module | Interface responsibility | May depend on |
| --- | --- | --- |
| identity | login/logout, current Account, password/session invalidation | account persistence, audit |
| request-core | fields, lifecycle, validation, history visibility | clock, persistence, audit |
| requester | Requester use cases and view models | identity, request-core |
| technician | Technician queue/work use cases | identity, request-core |
| manager | triage, lifecycle review, accounts, catalogues, matching | identity, request-core |
| ai | three typed advisory operations, limits and telemetry | identity checks, role read models, SoCLaaS port |
| audit | append-only business Audit Events and Manager queries | persistence |
| operations | health, structured logging and deployment metadata | database readiness |
| web | role controllers/templates and error mapping | use-case interfaces only |

The SoCLaaS seam has two adapters: HTTPS production and deterministic fake test.
Persistence adapters live behind transaction-scoped repository interfaces.
Role modules do not call another role's controller or repository directly.

## Data model and transactions

Tables include user_account, spring_session tables, maintenance_request,
requester_update, work_log, request_completion_history, category, skill_tag,
technician_skill, audit_event, ai_invocation and rate_limit_bucket.

Key rules:

- UUID or database-generated immutable primary keys; display ID uses a PostgreSQL
  sequence constrained to FF-000001–FF-999999.
- case-insensitive unique normalized username/category/skill values;
- foreign keys and check constraints reinforce roles, enums, lengths and minutes;
- Request and mutable catalogue/account rows carry an optimistic version;
- lifecycle operations lock/re-read the Request and commit state plus audit in
  one transaction;
- completion attempts are historical rows so return/reopen does not erase prior
  evidence;
- Audit Events are append-only through application permissions;
- Flyway is the only production schema mutation path and refuses unknown newer
  schema versions.

Repositories accept an explicit transaction context for multi-write operations.
Tests assert rollback by injecting failure between business and audit writes.

## Web authentication and authorization

Spring Security form login uses username/password and generic failure text.
Passwords use a maintained adaptive salted hash supported by Spring Security;
the exact encoder parameters are pinned and tested at implementation.

Spring Session JDBC provides server-side browser sessions across stateless Vercel
executions. The cookie has no persistent Max-Age and no inactivity timeout is
presented as a product feature. Logout deletes the session. Each protected request
re-checks Account active state, role and session version; deactivation and
Manager password reset invalidate all target sessions.

CSRF tokens protect state-changing browser requests. Authorization is enforced
at controller and use-case seams, including Requester owner and Technician
current-assignment checks. Use POST for mutations, Post/Redirect/Get or HTMX-safe
responses, optimistic-version fields, centralized safe errors and framework
output encoding. Security headers include CSP suitable for local assets,
frame-ancestors none, nosniff, strict referrer policy and HSTS in Production.

## UI composition

Templates have a shared authenticated shell but separate Requester, Technician
and Manager navigation and routes. The header identifies role and username and
offers main workspace, change password, about and logout. Forms and HTMX fragments
receive only role-specific view models; persistence entities and AI raw responses
are never rendered directly.

## Initialization

Development/demo migration seeds the six MP2 usernames and Welcome123 only when
the database is empty and demo seeding is enabled; credentials are documented as
non-production. Representative Requests cover all lifecycle states atomically.

Production bootstraps one Manager only for an empty database using
BOOTSTRAP_MANAGER_USERNAME, BOOTSTRAP_MANAGER_DISPLAY_NAME and
BOOTSTRAP_MANAGER_PASSWORD supplied as deployment secrets. After successful
bootstrap the password secret is removed/rotated. Existing databases are never
reseeded.

## Configuration

Provider selection (user decision, 14 October 2026): use Supabase-hosted
PostgreSQL. This does not replace the approved Spring identity/session model.
`SUPABASE_SSL_ROOT_CERT` points to the downloaded CA for JDBC certificate
verification. Setup and connection modes are recorded in `docs/SupabaseSetup.md`
and ADR-004. Account/request schema and deployment acceptance remain unchanged.

Required environment groups are:

- database: SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME,
  SPRING_DATASOURCE_PASSWORD;
- profile/seeding/bootstrap: SPRING_PROFILES_ACTIVE, DEMO_DATA_ENABLED and the
  bootstrap values;
- SoCLaaS and AI limits from INTEGRATION-SPEC;
- deployment: APP_BASE_URL, APP_VERSION and health/monitoring settings.

Development and Production values are distinct. Startup validates non-secret
configuration and fails safely for unusable database/security configuration.
Missing AI configuration degrades only AI readiness.

## Testing

1. Pure unit tests: validation, lifecycle, visibility, matching, schema parsing
   and retry decisions.
2. Isolated PostgreSQL integration tests: migrations, repositories, transactions,
   constraints, Spring Session and concurrent optimistic updates.
3. Spring MVC/security tests: login, CSRF, role/owner/assignment denials, stale
   form versions, safe errors and separate navigation.
4. AI contract/security tests: fake adapter, context allowlists, schemas, limits,
   injection, failures and log redaction.
5. Browser tests in latest stable Chrome: one critical workflow per role,
   keyboard/focus/error behavior and AI fallback.
6. Performance test: 50 concurrent authenticated users and p95 non-AI target.
7. Deployment tests: Preview smoke, production-candidate smoke, health,
   rollback and backup restore.

Every test/display name cites requirement IDs. Tests use synthetic data and
never depend on a developer or Production database.

## CI/CD and Vercel Hobby promotion

Pull requests run formatting/static analysis, unit/integration/security tests,
build and secret/dependency scanning. Required checks plus one human approval
gate merge to master.

Deployment flow:

1. Pull-request commits receive a Vercel Preview deployment using Preview
   database, SoCLaaS key and variables.
2. After approved merge and green checks, GitHub Actions builds/deploys a
   Production candidate with production variables using Vercel CLI production
   deployment without assigning the production domain.
3. An independent smoke test calls health, login and one safe read against the
   candidate URL.
4. A human-approved job runs Vercel promotion to assign the production domain to
   that tested deployment without rebuilding.
5. Rollback promotes the previous known-good deployment; schema migrations must
   be backward-compatible across the rollback window.

This uses Local/Preview/Production only and does not rely on paid custom
environments. The exact CLI flags and Spring Boot container behavior must be
confirmed in a deployment spike before implementation claims immutable promotion.
If Hobby cannot satisfy this flow, the specification must be revised and approved;
do not silently deploy a different artifact.

Application/database/AI secrets and data remain separate between Preview and
Production. Deployment is team-managed through GitHub Actions, never through an
AI build-and-host environment.

## Operations

The health endpoint exposes status only, not secrets or detailed dependency
errors. Logs are structured and redacted under NFR-OPS-002. Production database
uses provider-managed daily backup or an encrypted automated export with the
same RPO/RTO. Record one restore rehearsal and one alert exercise before release.

Free-tier retention may be insufficient for evidence, so CI smoke results,
deployment IDs, restore evidence and sanitized incident/alert records are stored
in project logs without production secrets or personal data.

## Design acceptance

Before scaffolding, reviewers must confirm the module dependency direction,
transaction/concurrency approach, Spring Session feasibility, database test
adapter, SoCLaaS fake seam, Vercel deployment spike plan and environment
separation. Version/provider choices are recorded without changing behavioral IDs.
