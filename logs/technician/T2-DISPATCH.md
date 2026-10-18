# Technician T2 dispatch

- User approval: next iteration expressly approved after T1 report/preview; preparation requested again.
- Baseline: 57098d9da1c2af1ee12395a3ced774bfbece4781, technician-workflow, clean tree/index.
- Start/preparation evidence: 2026-10-18T08:40:41.493860+00:00; preparation includes spec/storage/T1 interfaces, exact contract/task packets and OS-temp/durable handoffs before workers.
- Scope: REQ-TECH-004-006/010-011, start work and append-only Work Logs plus shared auth/audit/forms rules.
- [Contract](T2-CONTRACT.md); [backend](T2-backend-task.md), [UI](T2-ui-task.md), [tests](T2-tests-task.md).
- Fresh backend gpt-6.1-sol/medium, UI gpt-6-luna/high, custom Tester gpt-6-luna/high concurrently; fresh independent reviewer gpt-6.1-sol/high when slot frees. At most3workers plus coordinator; no leaf delegation/builds/Git.
- Coordinator owns SecurityIntegrationTest, guides, TRACEABILITY/REUSE, evidence, sole runner, exact-path Git. Shared security/build/migrations unchanged unless demonstrated gap requires coordinated decision.
- Gates: APIs agreed before dispatch, early compile/fixture smoke, frozen input hashes, scoped tests, full check and bootJar, independent review plus fresh correction verification, local commit/report. No T3, push, merge, live DB or deployment.
- Prior failed setup lessons applied: explicit exact APIs/types, privilege-separated seeding, checked lifecycle, single class-scoped PG fixture where safe. Tokens unavailable; measure actual elapsed, no assumed savings.
