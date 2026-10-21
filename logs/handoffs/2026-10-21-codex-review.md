# Codex review setup handoff

- Source: root Codex; destination: next session/user account setup.
- IDs: NFR-DOC-001 / NFR-SEC-001; user explicitly requested migration and guidance.
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
