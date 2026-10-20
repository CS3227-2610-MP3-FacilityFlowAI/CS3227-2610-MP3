# 20 October 2026 login UI polish

- Date/timezone: 20 October 2026, Asia/Singapore.
- Agent/task: Codex; user requested closer input focus outline and red login errors.
- Authority: NFR-A11Y-001, UIX-017-020, approved user-interface specification.
- Inputs: login.html, workspace.css, technician.css; Ponytail skill applied.
- Change: workspace.css gives input/select/textarea a 2px ring with 1px offset.
  Login muted paragraph rule excludes role=alert, preserving existing danger red.
- Scope: shared stylesheet, reuse/traceability/evidence; no authentication,
  template behavior, scripts or database changes. Existing unrelated edits preserved.
- Verified: login template alert semantics and stylesheet load order inspected;
  CSS cascade inspected; git diff --check passed.
- Tests: no automated tests added or full Java suite repeated for this CSS-only
  change. Browser/computed-style and hosted visual verification not performed.
- Suggestions accepted: existing focus and danger-color conventions reused.
  No new component, dependency, class or !important override needed.
- Human teammate review: pending; no merge/promotion performed.
- Next: review updated Preview login focus and failed-login error visually.
