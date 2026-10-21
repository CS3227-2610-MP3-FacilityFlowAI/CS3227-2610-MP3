# FacilityFlow AI Agent Guide

## Repository identity

- Product: FacilityFlow AI
- GitHub organization: `CS3227-2610-MP3-FacilityFlowAI`
- Repository: `CS3227-2610-MP3`
- Submission branch: `master`
- Team GitHub usernames: `ngkhengyang`, `yooplo`, `yu-sutong`

## Product boundary

Build a deployed facilities-maintenance web application for Requesters,
Technicians, and Facilities Managers. Each role has one bounded SoC LLM
assistant. The LLM may draft or recommend, but it must never submit requests,
change priority, assign work, alter state, close cases, or bypass normal service
authorization.

## Working rules

1. Treat `workflow/specs/` as the source of truth. Do not implement a material
   requirement while its behavior or acceptance criteria remain unresolved.
2. Reference stable requirement IDs in implementation, tests, pull requests,
   documentation, and traceability records.
3. Keep authentication, authorization, validation, workflow transitions, and
   database writes deterministic and server-side.
4. Treat prompts, retrieved text, and model output as untrusted data. Minimize
   context, validate typed output, enforce rate limits and timeouts, and keep the
   core workflow usable when the LLM fails.
5. Require a teammate review before merging non-trivial work. A fresh verifier
   must check fixes made after failed tests or security findings.
6. Preserve unrelated or uncommitted teammate work. Never overwrite it to make
   a task easier.
7. Record verified summaries of AI-assisted sessions under `logs/`, excluding
   secrets and unnecessary personal data.
8. Record every MP2-derived file or section in `workflow/REUSE.md` as unchanged,
   adapted, concept-only, or new.
9. For every agent or session transition, follow the shared handoff protocol in
   `workflow/agents/README.md`; the model invokes `$facilityflow-handoff` to
   produce temporary context and a durable project handoff record.

## Code Review Rules

- Review against approved `workflow/specs/` behavior and cite stable requirement
  IDs where applicable. Report introduced, actionable failures with file locations
  and concrete scenarios; do not turn proposed behavior into requirements or
  describe unexecuted checks as passed.
- Check role and ownership authorization before loading AI context or writing data.
  LLM output stays advisory and untrusted; deterministic services enforce writes,
  transitions, typed validation, bounded resource use and manual fallbacks.
- For deployment/database changes, preserve separate development/Production
  credentials and databases, restricted grants/RLS, encrypted recoverable backups
  and human promotion gates. Distinguish verified evidence from configured or
  pending controls. AI reviews do not replace teammate approval or CI.

## Technician implementation workflow

For Technician implementation, use `workflow/agents/technician-workflow.md` and
`workflow/TECHNICIAN-PLAN.md` before dispatch unless the user requests single-agent
work. This scheduling/model configuration applies only to the Technician slice;
Requester and Manager work retain the shared process. Commit approved workflow
configuration and verified iteration artifacts separately using explicit paths.
Before each iteration, present its scope, deliverables, agents/models and checks;
wait for the user's explicit permission for that iteration before starting it.

## Ownership

- `yooplo`: Requester slice; authentication and access-control foundation.
- `ngkhengyang`: Technician slice; SoC LLM gateway and AI observability.
- `yu-sutong`: Facilities Manager slice; persistence, audit, CI/CD, deployment.

Each owner is responsible for their interface, service behavior, persistence
integration, authorization tests, documentation, and reflection evidence.
Members may contribute across slices, but cross-role work does not transfer the
assigned owner's accountability. A teammate must review non-trivial work.
