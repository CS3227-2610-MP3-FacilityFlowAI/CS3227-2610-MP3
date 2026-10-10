# Product Specification

Status: product and AI directions updated 10 October 2026; detailed workflow,
quality targets, and teammate approval remain pending.

## Project purpose

FacilityFlow AI is a deployed facilities-maintenance web application for
reporting, triaging, completing, and auditing maintenance work. It reuses the
MP2 FacilityFlow domain where recorded in the reuse ledger and adds a web
architecture, PostgreSQL, deployment, and one advisory SoC LLM feature per
role.

## Actors and coordination

- Requester
- Technician
- Facilities Manager

The role-specific planning focuses in `AGENTS.md` organize work but do not
restrict members to one role or code area.

## Shared lifecycle

The planned MP2 baseline is:

`OPEN -> ASSIGNED -> IN_PROGRESS -> COMPLETED -> CLOSED`

`CANCELLED` is a terminal alternative. Reassignment, return for rework,
reopening, corrections, and cancellation eligibility require explicit web
requirements before implementation.

## Core product rules

- Authentication, authorization, ownership, validation, transitions, and writes
  are deterministic server-side operations.
- Each role has a separate web interface and can complete its core workflow
  when the SoC LLM is unavailable.
- AI endpoints return advisory suggestions and do not share mutation code paths.
- Development and production use separate services, databases, configuration,
  secrets, and hostnames.
- Reused MP2 material is identified and quantified before merge and release.

## Role AI requirements

- `REQ-RQ-001`: Smart Report Assistant, defined in `ROLE-REQUESTER.md`.
- `REQ-TECH-001`: Work Plan Assistant, defined in `ROLE-TECHNICIAN.md`.
- `REQ-MGR-001`: Triage Assistant, defined in `ROLE-MANAGER.md`.

## Shared AI requirements

### REQ-AI-001 Advisory boundary

AI generation must not create or update a request, work log, assignment,
priority, account, audit transition, or workflow state. Users act through
separate ordinary endpoints that repeat all applicable checks.

### REQ-AI-002 Core workflow fallback

Every role can complete its normal workflow after an AI timeout, rate limit,
malformed response, unavailable service, or user decision to skip AI. Existing
form input and authorized records remain unchanged by the failure.

## MVP exclusions

- Arbitrary web browsing or retrieval from unapproved documents.
- Model tool access or autonomous business actions.
- Image analysis, email ingestion, real-time chat, inventory, invoicing,
  payments, maps, and push notifications.
- Automatic grading of safety, urgency, technician performance, or user
  behavior.

## Non-functional requirements

The application must be secured, production-level, deployed, and supported by
testable AI-security, SDD, and multi-agent evidence. Measurable availability,
performance, accessibility, retention, recovery, and operational targets remain
pending in the technical specification.

## Unresolved questions

Approve the complete authorization matrix, account lifecycle, all ordinary
workflow fields and transitions, quality targets, retention, support contact,
and exact AI limits and failure responses.
