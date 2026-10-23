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

## Product website

The [product website source](docs/site/index.html) introduces the three roles,
maintenance lifecycle and advisory AI features, with links to the live app,
guides and team. It is configured for GitHub Pages at
<https://CS3227-2610-MP3-FacilityFlowAI.github.io/CS3227-2610-MP3/> once enabled
and deployed from `master`. See [Product Website](docs/ProductWebsite.md) for
publishing and local preview instructions. The application itself is live at
[FacilityFlow AI](https://facilityflow-ai.vercel.app).

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

The three role workflows and advisory SoCLaaS features are implemented.
Production deployment, database separation, protected promotion and rollback
were verified on 21 October; successful scheduled backup and monitor executions
were observed on 22 October. Current human approval is resolved by stakeholder
instruction. See [Acceptance](docs/Acceptance.md) for exact evidence and the
remaining technical qualifications; historical logs retain earlier statuses.

## Development workflow and setup

Start with [AGENTS.md](AGENTS.md) and [workflow/](workflow/README.md). Approved
[specifications](workflow/specs/README.md) govern implementation; material TBDs
block coding. Stable IDs connect specs, code, tests, PRs and evidence. Teammate
review precedes non-trivial merges; fresh verification follows failed-test or
security fixes.

Use the [Developer Guide](docs/DeveloperGuide.md) for local build/run commands,
[Deployment](docs/Deployment.md) for separate Preview/Production configuration,
and [Operations](docs/Operations.md) for backup, recovery and monitoring.
Source-controlled migrations and disposable tests remain separate from hosted
data. The app is live at [FacilityFlow AI](https://facilityflow-ai.vercel.app).

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

- [User Guide](docs/UserGuide.md): role workflows, AI review and manual fallback.
- [Developer Guide](docs/DeveloperGuide.md): architecture, setup and verification.
- [Reflections](docs/Reflections.md): team lessons on AI Security, SDD and Multi-Agent SE.
- [AI logs](logs/README.md): sanitized summaries and actual review status.

## AI usage acknowledgement

AI tools assist development/review; humans remain accountable for decisions,
verification and approvals. Record actual use and accepted/rejected suggestions
under logs/ without secrets or unnecessary personal data. Application AI must
use the SoC LLM and remain advisory. Codex GitHub development review is separate from
application AI and does not replace teammate approval or CI. See the reuse ledger for MP2 acknowledgements.

## Test Accounts

### Manager
Username: manager1
Password: Welcome123

### Requester
Username: requester1
Password: Welcome123

### Technician
Username: technician1
Password: Welcome123
