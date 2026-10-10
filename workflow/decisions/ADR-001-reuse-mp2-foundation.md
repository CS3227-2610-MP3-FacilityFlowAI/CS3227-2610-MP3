# Reuse MP2 FacilityFlow Foundation

- Decision ID: ADR-001
- Date: 2026-10-10
- Status: approved direction; reuse evidence pending
- Related artifacts: `workflow/REUSE.md`, `workflow/PLAN.md`

## Context

The team considered replacing FacilityFlow with a new product because MP3
requires a deployed web application. On 10 October 2026, the user reported that
the lecturer confirmed the team may reuse MP2 and add the required LLM
features. MP3 must still use a web interface, a production database, and a
separately managed deployment, and the team must document reused material.

## Decision

Use MP2 FacilityFlow as the domain and behavioral foundation. Reuse selected
domain rules, role permissions, models, validation, tests, and factual
documentation where appropriate. Replace the JavaFX interface and SQLite
storage with the approved web and PostgreSQL architecture. Record every reused
component in `workflow/REUSE.md` before merging it.

## Consequences

- The team can preserve tested workflow knowledge and focus MP3 effort on the
  web architecture, database, deployment, AI features, security, and evidence.
- Reuse claims must distinguish unchanged, adapted, concept-only, and new work.
- Planned estimates cannot be submitted as actual reuse measurements.
- A teammate must verify the ledger and final comparison evidence.

## Approval evidence

The user reported the lecturer clarification and instructed Codex to update the
repository on 10 October 2026. Teammate review of this record remains required.
