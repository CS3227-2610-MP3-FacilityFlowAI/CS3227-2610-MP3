# Technician T1-T Task Packet (prepared, not authorized)

- Iteration/role: T1-T, requirement-based testing; gpt-6-luna / high (user-selected).
- User approval: packet preparation only; T1 dispatch/implementation unapproved.
- Baseline: T0 commit containing this packet; inspect current Git state at dispatch.
- Inputs: [acceptance map](../../workflow/technician/ACCEPTANCE-MAP.md),
  [contracts](../../workflow/technician/CONTRACTS.md), exact linked requirements,
  existing DatabaseMigrationTest embedded PostgreSQL fixture and security tests.
- Exact write ownership: new files under
  `src/test/java/sg/edu/nus/facilityflow/`:
  `technician/TechnicianQueryServiceTest.java`,
  `technician/JdbcTechnicianQueryRepositoryTest.java`,
  `web/TechnicianQueueControllerTest.java`; optional dedicated
  `technician/TechnicianQueryFixtures.java`; uniquely assigned T1 handoff only.
- Goal: normal, forbidden, boundary and failure coverage for
  REQ-TECH-001-003/009 plus persisted identity, safe UI/date/filter behavior.
  Derive outcomes from specs, not backend implementation details.
- Cases: two-assignee isolation, reassignment/current historical authorship,
  retained terminal assignments, unfiltered counts vs all-state total, literal
  wildcard/case/trim search, combined filters/reset, invalid filter 400 vs unknown
  category zero rows, safe 404/503, UTC/local labels, exact priority/urgency/time/ID
  order including migrated null times and ties; repeatable snapshot/account changes.
- Dependencies: author from fixed contracts immediately; execute only once
  implementation is integrated/frozen and coordinator assigns this runner.
- Execution: isolated embedded PostgreSQL for SQL/roles/transactions; full
  migrations with synthetic data. H2 MVC fixtures do not prove PostgreSQL behavior.
  Reuse existing dependencies; coordinate build/shared fixture changes.
- Proposed Windows commands: `./gradlew.bat test --tests
  "sg.edu.nus.facilityflow.technician.TechnicianQueryServiceTest" --tests
  "sg.edu.nus.facilityflow.technician.JdbcTechnicianQueryRepositoryTest" --tests
  "sg.edu.nus.facilityflow.web.TechnicianQueueControllerTest"`; integrated
  `./gradlew.bat check` after scoped checks. Commands are not execution evidence.
- Resource/results: one slot, no production edits/Git/delegation/test weakening.
  Preserve other agents' work. Report actual commands/results, coverage gaps and
  reproducible failures; use facilityflow-handoff at task transitions. Fresh
  independent verification closes corrected findings; two failed cycles replan.
