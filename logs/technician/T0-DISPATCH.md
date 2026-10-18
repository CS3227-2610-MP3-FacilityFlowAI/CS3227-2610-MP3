# Technician T0 Dispatch

- Approval: user requested committed Technician-scoped configuration, then
  implementation of the first previewed iteration (T0 contracts/planning).
- Baseline: configuration commit 7c8f214799167a4e1f371f8ddae23af986533df0.
- Start: 18 October 2026, 14:45:38 Asia/Singapore (06:45:38 UTC).
- Root: C:/Users/User/Documents/NUS/CS3227/CS3227-2610-MP3/CS3227-2610-MP3.
- Coordinator: owns CONTRACTS.md, T1 packets, integration, checks, report and Git.
- Goal: concrete T1 contracts plus boundaries for later iterations, requirement
  test mapping and non-overlapping T1 ownership. No application implementation.
- Authority: approved [Technician requirements](../../workflow/specs/ROLE-TECHNICIAN.md),
  applicable [Product shared rules](../../workflow/specs/PRODUCT-SPEC.md) and
  [Technical contract](../../workflow/specs/TECHNICAL-SPEC.md).
- Workflow: [Technician protocol](../../workflow/agents/technician-workflow.md).
  Read only assigned sections/dependencies. Three fresh-context agents run in
  parallel. Each preserves other workers' files, performs no Git mutation or
  leaf delegation, and has read-only application-code scope.
- Handoff: use facilityflow-handoff and the shared handoff template. Each owns
  its unique durable result below, with a compact temporary handoff under the OS
  temp directory. Cite inspected paths/commands and actual findings. No test
  execution is needed for planning; distinguish existing coverage from proposals.
- Completion: agent outputs reconciled by coordinator; check links, requirements,
  DTO/route consistency, ownership, unchanged application and Git scope. Commit
  only T0 planning/evidence and report actual outcomes. T1 remains unapproved.
- Metrics: record actual models/effort and times; token counts unavailable unless
  exposed by the runtime. No comparison/savings claim without equivalent baseline.

## T0-B: Backend planning

- Model/effort: gpt-6.1-sol / medium.
- Read: architect role; Technician manual requirements; Product lifecycle,
  shared authorization/transaction rules; Technical module/data rules. Inspect
  identity/IdentityAuthorization.java, CurrentAccountProvider implementations,
  technician/ existing Work Plan repository/service and V1/V2/V4 migrations.
- Write ownership: logs/handoffs/2026-10-18-technician-T0-backend.md only, plus
  its OS-temp handoff. Include the technical proposal in that durable handoff.
- Deliver: concrete T1 service/repository signatures, domain DTO fields/enums,
  scope/search/filter/order and safe failure contract, exact proposed new paths,
  reusable seams and transaction/version/audit boundaries for T2/T3. Separate
  design choices from product rules; flag only real blocking ambiguities.
- Dependencies: approved requirements and baseline code, not UI implementation.
- Result: normally <=650 words in durable proposal, <=250-word message summary.

## T0-U: UI planning

- Model/effort: gpt-6-luna / high (explicit user selection).
- Read: developer role; Technician screens/manual requirements; Product UI/date/
  visibility rules; Technical UI/web sections. Inspect web/DashboardController,
  SecurityConfiguration, TechnicianWorkPlanController and templates/*.html.
- Write ownership: logs/handoffs/2026-10-18-technician-T0-ui.md only, plus its
  OS-temp handoff. Include the UI proposal in that durable handoff.
- Deliver: concrete T1 routes/query params/model attributes, required DTO fields,
  template/fragment layout, errors/empty/filter/reset/history behavior, exact
  proposed UI paths and coordinator-owned shared changes. Outline later forms,
  version/failure behavior and advisory AI UI without implementing them.
- Dependencies: approved behavior and current web seams; reconcile DTOs later.
- Result: normally <=650 words in durable proposal, <=250-word message summary.

## T0-T: Requirement-to-test planning

- Model/effort: gpt-6-luna / high (explicit user selection).
- Read: tester role; REQ-TECH-001-017/acceptance scenarios; relevant Product
  shared rules and Technical testing section. Inspect build.gradle and scoped
  database, identity/security and Technician/Work Plan tests for reusable fixtures.
- Write ownership: workflow/technician/ACCEPTANCE-MAP.md and
  logs/handoffs/2026-10-18-technician-T0-tests.md, plus its OS-temp handoff.
- Deliver: all 17 requirement IDs mapped to iteration, normal/forbidden/boundary/
  failure scenarios, test layers and exact proposed T1 test paths. Use existing
  isolated PostgreSQL harness for SQL/transaction behavior; no live database or
  SQLite. Identify existing AI evidence versus missing manual tests, T1 sorting
  fixtures, and commands to execute later. Do not author/run application tests.
- Dependencies: approved criteria, not implementation; avoid locking tests to
  invented internals. Coordinator reconciles names with final T1 contracts.
- Result: concise acceptance map; <=250-word message summary and compact handoff.
