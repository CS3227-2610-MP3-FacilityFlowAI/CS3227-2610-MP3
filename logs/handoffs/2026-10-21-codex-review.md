# Codex review setup handoff

- Source: root Codex; destination: next session/user account setup.
- IDs: NFR-DOC-001; user explicitly requested migration and guidance.
- Scope: separate chore/codex-review branch; remove old action/config, concise
  AGENTS.md review rules, CodeReview guide, Developer Guide link and evidence.
- No application, CI gate, production environment or secret changes.
- Verified: configuration checks and diff check; account connection/review unverified.
- User page: chatgpt.com/settings/code-review, GitHub disconnected. Dropdown is
  provider selector. Guided Back to app > Code Review > connect source account.
- Next: guide browser GitHub authorization/org repo selection, enable automatic
  review, user posts @codex review on a concrete PR, inspect actual result; then
  teammate review/merge. No token copying or API-key substitution.
- Skills: OpenAI Docs (official fetched pages), Ponytail, facilityflow-handoff.
- Preserve unrelated deployment-acceptance PR27; migration branched master.
- No broader merge/message permission is delegated. Status: setup in progress.

## Current follow-up

Repository now visible and shared Automatic review enabled per user screenshot.
Root independently verified Codex bot review5595582707 at commit434318cb70 on PR29.
CI/tests/image/Preview smoke passed that revision. User account setup verified by
actual review, no longer pending. Accepted bot findings about unrelated security
requirement ID and stale README references; corrected scoped docs/traceability.
Fresh verifier: read-only review these fixes, no cloud mutation, commits or merge.
Next root action: push reviewed documentation fix, mark PR ready, inspect new
Codex review if triggered; teammate approval/merge still required. No extra auth
or permission is delegated. Suggested skills: OpenAI Docs, Ponytail, handoff.

Fresh verifier confirmed both scoped documentation fixes and whitespace/reference
checks; no blockers, no application/CI/human-gate changes. Remote review evidence
was verified by root; fresh verifier checked local documentation only.
