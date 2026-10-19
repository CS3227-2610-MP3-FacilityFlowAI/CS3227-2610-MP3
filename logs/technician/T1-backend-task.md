# Technician T1-B Task Packet (prepared, not authorized)

- Iteration/role: T1-B, backend implementation; proposed gpt-6.1-sol / medium.
- User approval: T0 preparation authorized; T1 implementation awaits explicit
  approval of its preview. No dispatch authority is granted by this packet.
- Baseline: T0 commit containing this packet; inspect current Git state at dispatch.
- Inputs: [contracts](../../workflow/technician/CONTRACTS.md),
  [acceptance map](../../workflow/technician/ACCEPTANCE-MAP.md),
  [Technician manual rules](../../workflow/specs/ROLE-TECHNICIAN.md), Product
  authorization/history/UI rules and Technical module/data sections.
- Goal: assigned-only consistent dashboard/queue/detail reads for
  REQ-TECH-001-003/009 and applicable REQ-SH-002/008-010/013.
- Exact write ownership: only five new files under
  `src/main/java/sg/edu/nus/facilityflow/technician/`:
  `TechnicianQueryService.java`, `TechnicianQueryRepository.java`,
  `JdbcTechnicianQueryRepository.java`, `TechnicianReadModels.java`,
  `TechnicianQueryException.java`; plus its uniquely assigned T1 handoff.
- Contract: implement the signatures/records/errors in CONTRACTS.md; persisted
  active/role/session checks and current-assignment queries share one read-only
  repeatable snapshot. Never expose Account entities/secret material to web.
- Dependencies: fixed T0 contracts permit concurrent UI/test authoring. Propose
  any interface changes to coordinator before touching consumers.
- Completion: all scoped criteria mapped to passing service/PostgreSQL/MVC
  checks; no broadening on invalid filters/failures; deterministic literal search,
  filtering/order and correctly authored full assigned history.
- Verification: Tester authors independently; coordinator designates one runner
  after affected writers pause. Use isolated PostgreSQL, no live database.
- Shared files: identity, audit, existing gateway, migrations, build, security,
  dashboard routing and documentation remain coordinator-owned.
- Resource/results: one worker slot, no delegation/Git mutation. Preserve others'
  work; report paths, checks, findings and next dependency within 300 words plus
  failure evidence. After two unsuccessful repair cycles, report/replan.
- Evidence: coordinator creates T1 iteration record and a uniquely named
  logs/handoffs/ record at actual dispatch; use facilityflow-handoff.
