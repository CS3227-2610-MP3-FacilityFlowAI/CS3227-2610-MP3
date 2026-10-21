# Codex review migration

NFR-DOC-001. User requested replacing the API-backed PR Agent with
Codex GitHub review using their ChatGPT account and step-by-step setup guidance.

Prepared on separate chore/codex-review branch from master. Removes old PR Agent
workflow and unused .pr_agent.toml; ports bounded, repository-specific rules to
AGENTS.md. Adds CodeReview guide and developer link. Codex review is advisory;
CI and required teammate approval remain. Automatic PR description generation
is not provided by the replacement. GitHub secrets are unchanged.

Verified: diff whitespace checks and one-off configuration checks pass; old
workflow/config removed and no remaining workflow references to OPENAI_KEY or
PR Agent action. ci-cd.yml byte-equivalent to master after newline normalization.
GitHub branch protection query returned404 and ruleset list empty; no stale
required PR Agent rule observed or changed. Application tests not rerun for this
workflow removal/documentation change; normal PR CI still runs.

Initial setup checkpoint (superseded by verified result below): user screenshot of Code Review settings reports no
connected GitHub account and repository list empty. Provider dropdown only
switches GitHub/GitLab; it is not the connection control. User guided to open
Code Review app and connect source account. Do not claim automatic review works
until an actual review on the target PR revision is observed. Account allowance,
organization installation approval and repository setting require browser access.

Official references consulted:
https://learn.chatgpt.com/docs/third-party/github
https://learn.chatgpt.com/docs/code-review
https://learn.chatgpt.com/docs/auth

## Verified hosted review

User screenshot now shows the organization repository with shared Automatic review
enabled. Independently queried PR29 reviews and confirmed an actual review from
chatgpt-codex-connector[bot] on commit434318cb7086e6d36613ba99f2d3bf4df073bea0:
https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/pull/29#pullrequestreview-5595582707
GitHub check results for that revision: tests/package, secret scan, container
build and Preview/authenticated smoke all passed. No API credits were added by
agent; no account token or secret was copied into GitHub Actions.

Accepted two actionable review findings: remove unrelated NFR-SEC-001 mapping
(the migration implements documentation/review tooling, not application security)
and update README's deleted-workflow/old-reviewer references. Focused source and
reference checks verify these documentation corrections. Fresh verifier requested
under AGENTS.md rule5; new revision review and teammate merge remain pending.
