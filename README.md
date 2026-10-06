# FacilityFlow AI

FacilityFlow AI is the CS3227 MP3 continuation of the team's MP2 facilities
maintenance application. MP3 preserves the proven Requester, Technician, and
Facilities Manager workflow while rebuilding the product as a deployed web
application with guarded SoC LLM assistance for every role.

## Project direction

- **Requester:** report assistant for structured drafts, category suggestions,
  missing information, and explained urgency suggestions.
- **Technician:** work-plan assistant for diagnostics, safety warnings,
  evidence collection, and clarification questions.
- **Facilities Manager:** triage assistant for category, priority,
  clarification, and technician-skill recommendations.
- **AI boundary:** AI drafts and recommends only. Authenticated users review
  every result and perform all business actions through deterministic services.
- **Stack:** Java, Spring Boot, Thymeleaf/HTMX, PostgreSQL, Spring Security,
  Docker, GitHub Actions, and the SoC LLM.
- **Deployment:** separate development and production services and databases;
  production promotes the same tested container image digest.

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

## MP2 reuse

The assignment permits MP2 reuse when it is quantified and acknowledged.
FacilityFlow AI will reuse selected domain rules, role permissions, models,
validation, and test scenarios from MP2. The JavaFX interface and SQLite
storage will not be reused. All reuse must be recorded in
[`workflow/REUSE.md`](workflow/REUSE.md).

## Status

Planning baseline created on 4 October 2026. Implementation must follow the
specification gates in `workflow/PLAN.md`.

## Development workflow and setup

Start with [AGENTS.md](AGENTS.md) and [workflow/](workflow/README.md). Approved
[specifications](workflow/specs/README.md) govern implementation; material TBDs
block coding. Stable IDs connect specs, code, tests, PRs and evidence. Teammate
review precedes non-trivial merges; fresh verification follows failed-test or
security fixes.

Current contents are planning documents and scaffold templates. Application
code, automated application tests and deployment are not implemented. The stack
is an existing planning choice; build tool, versions, local commands, environment
variables and detailed technical approval are TBD. Setup instructions follow
[TECHNICAL-SPEC.md](workflow/specs/TECHNICAL-SPEC.md) approval.

| Additional path | Purpose |
| --- | --- |
| `.github/workflows/pr-agent.yml` | Existing PR-Agent automation |
| `.github/pull_request_template.md` | Requirements/security/evidence checklist |
| `workflow/agents/` | Bounded development-agent roles |
| `workflow/templates/` | Requirement, handoff, security-test and log templates |
| `workflow/decisions/` | Individual architecture/security decisions |

## Documentation

- [User Guide](docs/UserGuide.md): released behavior; currently placeholders.
- [Developer Guide](docs/DeveloperGuide.md): design/process and implementation TBDs.
- [Reflections](docs/Reflections.md): evidence prompts, not completed reflections.
- [AI logs](logs/README.md): sanitized summaries and actual review status.

## AI usage acknowledgement

AI tools assist development/review; humans remain accountable for decisions,
verification and approvals. Record actual use and accepted/rejected suggestions
under logs/ without secrets or unnecessary personal data. Application AI must
use the SoC LLM and remain advisory. PR-Agent development review is separate from
application AI. See the reuse ledger for MP2 acknowledgements.
