# 2026-10-12 Specification Draft

- Date/timezone: 12 October 2026, Asia/Singapore.
- AI/tool/agent role: Codex acting as Analyst and Architect; no subagents used.
- Task: Draft the FacilityFlow AI specification baseline and give each bounded
  development-agent role only the context needed for its task.
- Prompt/intention summary: Preserve confirmed MP2 behavior, adapt it to a
  deployed web/PostgreSQL application, specify one guarded SoCLaaS feature per
  role, record Vercel promotion, and avoid implementation.
- Sources/approved revision: stakeholder decisions through 12 October 2026;
  binding group-planning document; MP2 master commit
  9819b857d880134c1fc1ffef8a8bcd5d6e536f43; local SoCLaaS and lecture notes.
  The new specification revision is draft v0.1 and is not approved.
- Requirement IDs: 125 draft IDs across REQ-SH, REQ-RQ, REQ-TECH, REQ-MGR,
  REQ-AI, AI-SEC and NFR prefixes.
- Files affected: CONTEXT.md; workflow/specs; workflow/TRACEABILITY.md;
  workflow/PLAN.md; workflow/REUSE.md; workflow/SECURITY.md;
  workflow/README.md; workflow/agents/README.md and the two new verifier role
  files; README.md; docs/DeveloperGuide.md.
- Important decisions: shared rules live once in PRODUCT-SPEC; role files own
  role behavior and human approval; INTEGRATION-SPEC owns typed SoCLaaS
  contracts; AI-SECURITY-SPEC owns threats/controls; TECHNICAL-SPEC owns module
  and Vercel design. Specifications remain draft until pull-request approval.
- AI suggestions accepted: deep module seams, minimum-reading routes, strict
  typed schemas, deterministic Technician candidate ranking, PostgreSQL-backed
  sessions/limits where required, and sanitized handoff evidence.
- AI suggestions rejected: autonomous orchestration, implementation work,
  production access, tool-enabled LLM behavior, and duplication of full shared
  context in every role file.
- Verification commands/results/evidence: local validation found 125 unique
  requirement IDs, every ID covered by TRACEABILITY, every checked local
  Markdown target present, no material specification placeholders, and
  git diff --check passed. These are document checks, not application tests.
- Corrections/unresolved questions: corrected Technician deactivation behavior
  to preserve MP2 semantics; added the two specialist verifier definitions from
  the binding seven-role plan. Provider URLs/credentials/model/quota, managed
  PostgreSQL vendor, deployed URLs and exact compatible dependency versions are
  intentionally configuration-time values. Vercel/Spring Boot feasibility and
  non-functional targets still require later execution evidence.
- Security considerations: no credentials or raw prompts/outputs were recorded.
  AI remains advisory and all business authorization remains deterministic.
- Human reviewer/outcome: pending team review and pull-request approval.
