# Codex pull-request review

NFR-DOC-001 / NFR-SEC-001: use the hosted Codex GitHub integration for advisory
reviews. Teammate approval and deterministic CI remain required by AGENTS.md.
No new GitHub Actions review workflow or OpenAI API secret is needed for this
integration. It uses the connected account's available Codex review allowance.

## Account setup

1. Connect the intended GitHub account to Codex and grant the integration access
   to CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3. Organization installation
   approval may require an owner. Select only this repository when possible.
2. Open https://chatgpt.com/settings/code-review and select the repository.
3. Enable Automatic review for the team repository; choose All PRs if available.
   If repository settings follow personal preferences, enable Automatic review in
   your personal preferences too and select the desired Review trigger.
4. Keep Exhaustive code review and Use credits for reviews off for the initial
   setup. Account access/allowances and GitHub app installation are browser-side
   settings; committing these files does not enable them.

## Verify on an open pull request

Comment `@codex review` yourself, then verify a reaction/review from Codex on that
PR's current revision. An empty finding list is not teammate approval. Capture the
review URL under logs only after it exists. If nothing happens, check repository
access, the connected GitHub identity, review settings and account allowance.

Repository-specific review instructions are in AGENTS.md, not .pr_agent.toml.
Keep PR descriptions accurate using .github/pull_request_template.md; the old
automatic PR-description feature is not replaced by Codex review.

The old API-backed PR Agent workflow/config are removed by this migration. They
remain on master until the migration PR merges; existing failures do not become
successful reviews retroactively. Do not add a dummy success check or weaken CI
to hide them. If a stale PR Agent check is required by a future branch rule,
replace that obsolete requirement without removing build/security/human gates.
The migration leaves existing GitHub secrets untouched.

Official setup and review instructions:
https://learn.chatgpt.com/docs/third-party/github
