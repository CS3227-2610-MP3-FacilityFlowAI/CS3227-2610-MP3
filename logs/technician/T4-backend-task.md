# T4 backend task packet

Ownership addendum before correction: WorkPlanContext.java in ai/technician is
assigned for the demonstrated signed-Java versus PostgreSQL UUID ordering gap.
Use canonical UUID ordering for latest-ten ties and oldest-first output; no other
provider changes. Tester owns the high-bit regression. Return handoff includes it.

- User approval: T4, “Go ahead with the implementation”; baseline 1640606 clean checkout.
- Role/model: Developer, gpt-6.1-sol / medium, fresh leaf context.
- Goal: Implement the two demonstrated authorization/freshness gaps and short consistent context snapshot per T4 contract. No provider rewrite. Notify root only if contract cannot safely be implemented.
- Contract and acceptance: [T4-CONTRACT](T4-CONTRACT.md), authoritative REQ-TECH-012–017/TECH-AI01–04 and linked AI-SEC rules. Validate these sources. Read scoped role document workflow/agents/developer.md and minimum-reading table in workflow/specs/README.md; expand only when necessary with reason.
- Exclusive ownership: src/main/java/sg/edu/nus/facilityflow/technician/TechnicianWorkPlanService.java; TechnicianWorkPlanRepository.java; JdbcTechnicianWorkPlanRepository.java (same directory). Also own logs/handoffs/2026-10-18-technician-T4-backend.md and an OS-temp return handoff. Root owns all other shared files/evidence. No other file without explicit reassignment.
- Interface: exact methods, endpoint, JSON, DOM IDs, errors, ordering and snapshot semantics fixed in T4-CONTRACT.md. Existing WorkPlanContext and provider/schema are inputs, not write scope. Tests may author against the fixed interface while backend/UI changes concurrently.
- You are not alone in this codebase. Preserve other agents' edits, never revert them. Shared checkout: author only, no builds/tests/Git/delegation/live network/database. Coordinator sole runner after writers pause; no dependency/build/migration edits. Maximum three leaf slots. Report blocking interface issues promptly, otherwise one batched result.
- Verification: compile + minimal PG/script preflight; frozen candidate independent review and scoped tests; batched corrections and independent delta verification; final integrated check/bootJar. Report actual checks only; authoring is not execution. Replan after two failed repair cycles.
- Return <=300 words: paths, requirement coverage, unverified cases, issues and next dependency. Use .agents/skills/facilityflow-handoff/SKILL.md and workflow/templates/agent-handoff-template.md for durable + temp return record. Reference sources instead of copying transcript. Metrics remain conversation only; no runtime/token data in committed evidence. Do not commit/push/merge.
