# Reuse and AI Feature Decision

## Goal

Update the MP3 repository after the team reported lecturer confirmation that
MP2 reuse is permitted, and specify one LLM-based feature for each FacilityFlow
role.

## Human input

- The user reported that the lecturer permits MP2 reuse.
- The resulting MP3 product must be a web application with a production
  database and deployment.
- The team must document reused MP2 material.
- The user accepted the proposed Smart Report, Work Plan, and Triage assistants
  and requested a repository update.

## Sources checked

- Existing MP3 repository planning and specification files.
- MP2 commit `cbfeb8fec339d2708c15eae1884fbc9b69063908`, dated
  29 September 2026.
- MP2 validation bounds and request lifecycle enums, used only to keep the
  drafted contracts consistent with the planned reuse baseline.

## Changes

- Recorded the reuse clarification and AI feature decisions as ADRs.
- Clarified that role assignments are coordination focuses, not exclusive work
  boundaries.
- Defined typed inputs, outputs, human approval boundaries, failure behavior,
  and draft acceptance criteria for all three AI features.
- Allocated traceability IDs and updated reuse, security, integration, planning,
  and guide material.
- Updated the first group-planning document to match the repository.

## Verification

- Markdown links and repository consistency checked locally.
- The Word planning document was rebuilt, rendered, and visually inspected.
- No application code, model integration, security control, database, or
  deployment was implemented in this update.

## Human review

User direction is recorded. Teammate review is required before merging this
non-trivial documentation and specification change.
