# Technician T1-U Task Packet (prepared, not authorized)

- Iteration/role: T1-U, UI implementation; gpt-6-luna / high (user-selected).
- User approval: preparation only; T1 implementation requires explicit approval.
- Baseline: T0 commit containing this packet; inspect current Git state at dispatch.
- Inputs: [contracts](../../workflow/technician/CONTRACTS.md),
  [acceptance map](../../workflow/technician/ACCEPTANCE-MAP.md), Technician manual
  screens, Product UI/date/history rules and Technical web/UI sections.
- Exact write ownership: new
  `src/main/java/sg/edu/nus/facilityflow/web/TechnicianQueueController.java`;
  `src/main/resources/templates/technician/queue.html`,
  `src/main/resources/templates/technician/request-detail.html`,
  `src/main/resources/templates/technician/fragments/request-rows.html`,
  `src/main/resources/templates/technician/fragments/history.html`;
  plus its uniquely assigned T1 handoff.
- Deliver: GET queue/detail routes, DTO binding, counts/filter/reset/empty states,
  escaped assigned history and unambiguous local timestamps per CONTRACTS.md.
  Use exact backend models; implement no SQL/business authorization in controllers.
- Failure contract: invalid filter 400 with field errors/preserved submitted
  values and no broadened results; invalid/unknown/unassigned detail same safe
  404; operational 503 with manual retry. Preserve standard identity behavior.
- Dependencies: work against agreed DTOs while backend progresses. Coordinator
  wires existing `/technician` to the combined queue view and owns shared files.
  Shared login/Requester/Manager routes must remain intact.
- Exclusions: no write/AI buttons, dead shared-navigation links, new dependencies,
  entity exposure, unchecked return URLs or changes to existing shared templates.
- Completion: rendered MVC/security checks plus scoped accessibility/date/empty
  assertions pass. No tests/builds while affected shared-checkout writers run.
- Resource/results: one slot; no delegation/Git mutations. You are not alone;
  preserve others' edits and request ownership/interface changes. Return paths,
  actual checks, findings and next dependency within 300 words plus failure evidence.
- Evidence: coordinator assigns a unique T1 handoff path at actual dispatch;
  use facilityflow-handoff. Two unsuccessful repair cycles trigger replanning.
