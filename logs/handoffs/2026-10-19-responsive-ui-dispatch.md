# Responsive UI iteration: dispatch and task packets

- Source agent: coordinator.
- Destination agents: UI developer and Tester; independent verifier after freeze.
- Task/goal: modern, adaptive presentation without functionality changes.
- Approved requirement IDs: NFR-A11Y-001/002, REQ-SH-010/011/012/018,
  REQ-TECH-001/002/011/014/015/016.
- Approval: user requested UI/spec updates and tests; explicitly approved the
  styling-only Technician iteration in this session on 19 October 2026.
- Sources: `workflow/specs/PRODUCT-SPEC.md`, `ROLE-TECHNICIAN.md`,
  `workflow/agents/technician-workflow.md`, existing templates and stylesheets.
- Scope: presentation, UI specification adaptation, scoped tests and evidence.
  No service, database, authorization, AI or workflow changes.
- UI developer: gpt-6-luna, high effort. Own only
  `src/main/resources/templates/technician/queue.html` and `request-detail.html`.
  Add the shared `/css/workspace.css` after existing CSS. Inspect for presentation
  conflicts and report to coordinator, who owns all CSS. Preserve all expressions,
  routes, controls, hooks, safe defaults and hidden states. No delegation/Git.
- Tester: gpt-6-luna, high effort. Own only
  `src/test/js/requester-verification.cjs`. Extend existing real Chrome fixture
  verification for the shared stylesheet, 360/760/1024/1440 widths, resized unsaved
  input, hidden assistant visibility, and Manager/Technician read-only pages.
  Existing fixture has manager1/technician1 with the same synthetic password.
  Do not alter existing behavior assertions or write new business actions. Check
  Manager list/overview/admin pages and Technician queue; retain Requester load
  measurement. Coordinator runs Gradle after writers stop; no concurrent builds.
- Interfaces: unchanged HTML forms, fields, URLs, CSRF/version and JS data hooks.
- Acceptance: keyboard focus and labels, no page overflow, readable wrapped
  controls, visible AI safety text, form values survive resize, existing critical
  workflows pass. Test desktop Chrome; smaller width is additional design coverage.
- Shared ownership: coordinator owns CSS, other templates, docs/specs and evidence.
  All agents share checkout; preserve others' changes.
- Verification: pending. Freeze candidate and request independent review after
  preflight; fresh verifier checks corrections. No new database writes to smoke.
- Risks: visual regressions require actual browser checks; no hosted claim.
- Human review: teammate review required before merge; no push/deploy authorized.
- Next authorized action: scoped authorship, then coordinator tests and review.
- Status: ready for scoped implementation.
