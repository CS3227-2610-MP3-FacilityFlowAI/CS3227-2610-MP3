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
