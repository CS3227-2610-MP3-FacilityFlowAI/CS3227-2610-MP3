# Master Merge Conflict Resolution

## Goal

Merge `origin/master` at `274ffd3` into `spec-draft` and reconcile PR #2 with
the stakeholder-confirmed specification draft at `a916d3`.

## Authority and scope

The user authorized the merge and directed that confirmed decisions from the
specification conversation remain authoritative. The precedence rule in
`workflow/specs/README.md` was applied. No application implementation or
deployment state was changed.

## Resolution

- Resolved 23 textual conflict blocks across 11 workflow/specification files by
  retaining the later, comprehensive specification contracts.
- Preserved compatible PR #2 additions: ADR-001, ADR-002, the historical
  decision log, User/Developer Guide detail, and planning/reuse context.
- Corrected PR #2's older requirement IDs and output contracts. In particular,
  the Smart Report result has no requester safety-advice field, and Manager AI
  returns skill tags while deterministic application code ranks Technicians.
- Kept the verified MP2 baseline in `workflow/REUSE.md` and the Vercel deployment
  direction in the current technical specification.
- Added AI-SEC-028 through AI-SEC-030 and AIS-T16 through AIS-T18 for post-call
  authorization, CSRF/pre-provider ordering, and schema-valid leakage checks.

## Verification

- `git diff --cached --check`: passed.
- Conflict-marker and unmerged-path checks: none found.
- Requirement declaration/traceability check: 128 unique definitions and all
  128 covered by `workflow/TRACEABILITY.md`.
- Local Markdown link resolution check: passed.
- No application tests were available or run because this merge changes only
  planning, specification, process and guide artifacts.

## Review

The resulting specification remains a draft and requires the teammate approvals
recorded in the specification documents before implementation begins.
