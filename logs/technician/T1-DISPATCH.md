# Technician T1 Dispatch

- User approval: "Go with the next iteration" authorizes T1 after its T0 preview
  and prepared packets. This supersedes the historical unapproved status in the
  T0 packets for this iteration only. T2 and later remain unapproved.
- Baseline: 172eaff; branch technician-workflow; initial tree/index clean.
- Start: 18 October 2026, 15:00:51 Asia/Singapore (07:00:51 UTC).
- Goal: REQ-TECH-001-003/009 assigned dashboard/queue/detail/history reads and
  applicable shared authorization, filtering, dates, safe errors and UI behavior.
- Inputs: [contracts](../../workflow/technician/CONTRACTS.md),
  [acceptance map](../../workflow/technician/ACCEPTANCE-MAP.md), and prepared
  [backend](T1-backend-task.md), [UI](T1-ui-task.md), [test](T1-tests-task.md) packets.
- Backend: reuse T0-B worker context; gpt-6.1-sol / medium. Switch from planning
  to Developer role. Own the five new Technician query/model/exception files in
  the packet and logs/handoffs/2026-10-18-technician-T1-backend.md only.
- UI: reuse T0-U context; gpt-6-luna / high. Own its new controller and four
  templates, the coordinator-approved external
  `src/main/resources/static/css/technician.css`, and
  logs/handoffs/2026-10-18-technician-T1-ui.md only. Expose a public
  `String workspace(CurrentAccountPrincipal principal, Model model,
  HttpServletResponse response)` controller method for the coordinator's shared
  `/technician` route to reuse default-filter rendering and safe failure handling.
- Tester: fresh cs3227_unit_test agent; gpt-6-luna / high. Own the three test
  classes and optional fixture file in the packet and
  logs/handoffs/2026-10-18-technician-T1-tests.md. Apply requirement-based tests
  to the named new implementation files, using the approved MP3 PostgreSQL
  contract (not the personal skill's MP2 SQLite storage recipe). Existing
  JUnit 5 / Spring 7 tests use manual contexts; inspect existing examples before
  choosing incompatible Spring JUnit annotations.
- Coordinator: owns shared DashboardController wiring, existing security-test
  fixture compatibility and authenticated template-rendering regressions,
  guides, TRACEABILITY/REUSE, run records, Git and builds.
  Requester/Manager routes and existing AI code remain unchanged.
- Concurrent authoring only: no worker builds/tests until the coordinator freezes
  affected writers and assigns the single runner. No worker Git mutation or leaf
  delegation. Each owns a unique handoff and its OS-temp copy; preserve others'
  work and request ownership before changing any unassigned path.
- Contract clarifications: requireCurrentTechnician checks exact nonnegative
  persisted session version; read sessions stay inside a read-only repeatable
  snapshot; category UUID parsing rejects noncanonical/malformed identifiers;
  related histories remain current-assignment scoped. UI may implement controller
  workspace method without changing DTOs or adding a duplicate /technician route.
- Verification: scoped service/PostgreSQL/MVC tests, independent read-only
  review (gpt-6.1-sol / high, after a worker slot frees), required integrated
  Gradle check, and fresh verification of corrected findings. No live database.
- Completion: all scoped criteria have evidence, pending items explicit,
  exact-path commit and per-agent report. No write workflow or AI UI controls.
- Metrics: actual models/effort and overall elapsed time recorded; token usage
  unavailable unless exposed. No savings claim without a comparable measurement.
