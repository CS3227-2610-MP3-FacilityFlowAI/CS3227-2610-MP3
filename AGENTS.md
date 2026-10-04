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

## Ownership

- `yooplo`: Requester slice; authentication and access-control foundation.
- `ngkhengyang`: Technician slice; SoC LLM gateway and AI observability.
- `yu-sutong`: Facilities Manager slice; persistence, audit, CI/CD, deployment.

Each owner is responsible for their interface, service behavior, persistence
integration, authorization tests, documentation, and reflection evidence.
