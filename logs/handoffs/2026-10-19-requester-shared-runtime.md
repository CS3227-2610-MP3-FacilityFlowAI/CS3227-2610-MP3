# Requester shared-runtime consolidation handoff

- Source agent: Codex implementation session; destination: teammate PR reviewer.
- Task/goal: route Requester Smart Report UI through the existing shared SoCLaaS
  runtime and remove the duplicate gateway, transport and admission code.
- Approved IDs: REQ-RQ-012-017, REQ-SH-018, REQ-AI-001-006 and
  AI-SEC-001/008/017/021/028-030; workflow/specs is authoritative.
- Authorization: user requested shared-runtime correction, then commit/push on
  20 October. No Supabase operations or private configuration edit authorized.
  Never commit MP3 instructions or local credentials. Preserve unrelated history.
- Provenance/artifacts: git diff, docs/DeveloperGuide.md, docs/UserGuide.md,
  workflow/TRACEABILITY.md, workflow/REUSE.md and
  logs/2026-10-19-requester-shared-runtime.md. Existing MP3 code adapted; no MP2
  source copied. Form routes and review response contract remain compatible.
- Changes: thin ai.SmartReportService form adapter delegates to canonical
  RequesterSmartReportService; expected edit version checked before admission;
  category IDs resolved after admission. Shared lifecycle/coordinator/typed
  assistant/provider/telemetry own execution. Duplicate classes/tests removed;
  Requester HTTP tests now use the shared typed port and real admission backend.
- Assumptions: deployment keeps using approved shared runtime configuration;
  different rough text/current description share the 2000-code-point bound.
- Verification: full offline check bootJar requesterVerification successful in
  10m4s; 725 standard tests and one browser/load test, zero failures/errors/skips.
  Five Node suites passed; git diff --check passed. Browser checks passed at
  three viewports. 50 distinct users/400 requests, zero errors, p95 991.72ms
  (local manual workflow, AI excluded). Actual evidence is in ignored build logs
  and the durable session summary above.
- Failure/fix evidence: missing assertion import fixed; test singleton setup moved
  after bean-definition parsing to avoid replacement. Fresh runtime_verifier
  reviewed production architecture and corrected fixture/shared repository spy;
  no remaining blocking finding. No production validation bypass introduced.
- Security/limits: no AI business writes; no credentials/raw model text logged;
  no dependencies or migrations added. Adapter waits on shared bounded runtime and
  occupies a servlet thread. Earlier live smoke used the retired duplicate path;
  no live provider call in this verification. Hosted quality/performance unverified.
- Suggestions accepted: shared-context denial spy, accurate rate-test name,
  combined input limit in form/guide. No unresolved behavioral choice.
- Human approval: teammate review required before non-trivial merge; pending.
- Next authorized action: commit/push relevant changes to feat/requester-workflow.
  Follow-up live-provider smoke uses synthetic data and disposable PostgreSQL;
  never infer shared-runtime live success from historical duplicate-path evidence.
- Suggested receiver skills: Ponytail, facilityflow-handoff; Postgres best practices
  for any future SQL change. Handoff does not expand permissions.
- Status: automated checks verified; ready for teammate review.
