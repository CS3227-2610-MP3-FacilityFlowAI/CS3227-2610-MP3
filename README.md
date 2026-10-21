# FacilityFlow AI

FacilityFlow AI is the CS3227 MP3 continuation of the team's MP2 facilities
maintenance application. MP3 preserves the proven Requester, Technician, and
Facilities Manager workflow while rebuilding the product as a deployed web
application with guarded SoC LLM assistance for every role.

The team reported a lecturer clarification on 10 October 2026: MP2 may be
reused, provided MP3 is delivered as a web application with a production
database and deployment, includes the required LLM features, and documents the
reused material. The clarification is recorded in
[`ADR-001`](workflow/decisions/ADR-001-reuse-mp2-foundation.md).

## Project direction

- **Requester:** Smart Report Assistant for structured drafts, category and
  urgency suggestions, and missing-information questions.
- **Technician:** Work Plan Assistant for diagnostic steps, safety checks,
  suggested tools, requester questions, and evidence to collect.
- **Facilities Manager:** Triage Assistant for category, priority,
  clarification, and skill-tag suggestions followed by deterministic candidate
  ranking in the application.
- **AI boundary:** AI drafts and recommends only. Authenticated users review
  every result and perform all business actions through deterministic services.
- **Stack:** Java, Spring Boot, Thymeleaf/HTMX, PostgreSQL, Spring Security,
  Docker, GitHub Actions, and the SoC LLM.
- **Deployment:** Vercel Preview and Production use separate databases and
  secrets; a human promotes the smoke-tested Production candidate without
  rebuilding it.

## Repository layout

| Path | Purpose |
| --- | --- |
| `src/` | Application and test code |
| `workflow/` | Specifications, plans, traceability, reuse records, decisions, and Agentic SE artifacts |
| `docs/` | User guide, developer guide, reflections, and planning documents |
| `logs/` | Human-verified summaries of AI-assisted development sessions |

The approved execution baseline is in [`workflow/PLAN.md`](workflow/PLAN.md).
The full first group-planning document is in
[`docs/planning/MP3_First_Group_Planning_Document.docx`](docs/planning/MP3_First_Group_Planning_Document.docx).

## Team

| GitHub user | Role slice | Team-level responsibility |
| --- | --- | --- |
| `yooplo` | Requester | Authentication, sessions, role routing, CSRF, and account access controls |
| `ngkhengyang` | Technician | SoC LLM gateway, rate limiting, typed AI output, and AI observability |
| `yu-sutong` | Facilities Manager | PostgreSQL migrations, audit model, CI/CD, and deployment |

Members may contribute across role and shared features, but each assigned owner
remains accountable for completing their role slice. Every non-trivial change
requires teammate review.

## MP2 reuse

The assignment and the reported lecturer clarification permit MP2 reuse when it
is quantified and acknowledged.
FacilityFlow AI will reuse selected domain rules, role permissions, models,
validation, and test scenarios from MP2. The JavaFX interface and SQLite
storage will not be reused. All reuse must be recorded in
[`workflow/REUSE.md`](workflow/REUSE.md).

## Status

Planning baseline created on 4 October 2026. The reuse decision and three AI
feature contracts were updated on 10 October 2026. Specification baseline v0.1
was approved on 12 October 2026; implementation must follow the gates in
`workflow/PLAN.md` and the approved requirement IDs.

## Development workflow and setup

Start with [AGENTS.md](AGENTS.md) and [workflow/](workflow/README.md). Approved
[specifications](workflow/specs/README.md) govern implementation; material TBDs
block coding. Stable IDs connect specs, code, tests, PRs and evidence. Teammate
review precedes non-trivial merges; fresh verification follows failed-test or
security fixes.

Part 1 adds the Java 25/Spring Boot 4.1.1 build scaffold, Gradle 9.1.0 wrapper
and a passing startup test. Part 2a adds Supabase JDBC configuration and a
read-only connection checker; [Supabase setup](docs/SupabaseSetup.md) is required
before a live check. Part 3 supplies the remaining shared schema and opt-in
hashed account initialization; see [DatabaseCompletion](docs/DatabaseCompletion.md).
V1 is evidenced on Supabase; the user reports matching hosted upgrade/demo
initialization results. No independent hosted query was performed.
Authentication from master is integrated with the private schema and existing
seeded accounts. Role business features remain in separate branches. CI/CD and
the Vercel container/promotion workflow are defined but remain disabled until
the team supplies separated platform configuration and records hosted evidence.
See the [Developer Guide](docs/DeveloperGuide.md)
for local build/run commands and [Requester plan](workflow/REQUESTER-PLAN.md)
for the implementation sequence.

| Additional path | Purpose |
| --- | --- |
| `AGENTS.md`, `docs/CodeReview.md` | Hosted Codex GitHub review rules and setup |
| `.github/workflows/ci-cd.yml` | Test, scan, container, Preview and protected Production pipeline |
| `.github/workflows/rollback.yml` | Human-approved known-good Production rollback |
| `.github/pull_request_template.md` | Requirements/security/evidence checklist |
| `workflow/agents/` | Bounded development-agent roles |
| `workflow/templates/` | Requirement, handoff, security-test and log templates |
| `workflow/decisions/` | Individual architecture/security decisions |

## Documentation

Managers can open **AI monitoring** from their workspace or overview to inspect
aggregate assistant outcomes, durations, retries, token coverage and unresolved
invocations, and record a manual comparison with SoCLaaS portal usage. Shared
lifecycle telemetry and structured, correlated diagnostics support all three
assistants; no prompt or generated advice is retained. See the User and Developer
Guides for scope and operating instructions.

- [User Guide](docs/UserGuide.md): released behavior; currently placeholders.
- [Developer Guide](docs/DeveloperGuide.md): design/process and implementation TBDs.
- [Reflections](docs/Reflections.md): evidence prompts, not completed reflections.
- [AI logs](logs/README.md): sanitized summaries and actual review status.

## AI usage acknowledgement

AI tools assist development/review; humans remain accountable for decisions,
verification and approvals. Record actual use and accepted/rejected suggestions
under logs/ without secrets or unnecessary personal data. Application AI must
use the SoC LLM and remain advisory. Codex GitHub development review is separate from
application AI and does not replace teammate approval or CI. See the reuse ledger for MP2 acknowledgements.
