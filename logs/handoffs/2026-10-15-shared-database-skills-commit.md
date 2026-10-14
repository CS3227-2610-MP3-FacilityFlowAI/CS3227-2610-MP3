# Shared database skills handoff

- Source agent: Codex developer/documentation session.
- Destination agent: next FacilityFlow development session.
- Task/goal: commit relevant remaining development references.
- Approved requirement IDs: NFR-DOC-001, AI-SEC-025; no behavior change.
- Approved specification revision/evidence: current `workflow/specs/` and
  database decisions in `workflow/DECISIONS.md`.
- Authorized scope/permissions: user authorized relevant local commits only;
  instruction folder excluded; no push, merge, or hosted database changes.
- Relevant input artifacts/provenance: `skills-lock.json` records the
  `supabase/agent-skills` source; `workflow/README.md` documents precedence.
- Assumptions: existing downloaded skill bundles are shared development guidance.
- Unresolved questions: none for this commit.
- Files changed: two existing skill directories and lock file added to Git;
  workflow reference note and this session's log/handoff added.
- Commands/tools: Git inventory/diff, JSON validation, staged path and whitespace
  checks, credential-pattern scan, local commit and post-commit status.
- Verification performed/results/evidence: see
  `logs/2026-10-15-shared-database-skills-commit.md` and commit tool output;
  no application tests rerun for reference-only changes.
- Security considerations/unresolved risks: `.gitignore` still has a local edit
  disabling the instruction-folder exclusion; continue explicit staging.
- Suggestions accepted/rejected: accept skill references; exclude instruction
  folder and its disabled-ignore edit.
- Human approval required and reason: teammate review before any non-trivial merge.
- Human reviewer/outcome/evidence: pending.
- Next authorized action: report commit; future implementation follows approved
  specifications. Suggested skills: Ponytail, Supabase/Postgres, project handoff.
- Status: ready for review. This handoff grants no broader permissions.
