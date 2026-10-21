# Codex review migration

NFR-DOC-001 / NFR-SEC-001. User requested replacing the API-backed PR Agent with
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

Hosted activation pending: user screenshot of Code Review settings reports no
connected GitHub account and repository list empty. Provider dropdown only
switches GitHub/GitLab; it is not the connection control. User guided to open
Code Review app and connect source account. Do not claim automatic review works
until an actual review on the target PR revision is observed. Account allowance,
organization installation approval and repository setting require browser access.

Official references consulted:
https://learn.chatgpt.com/docs/third-party/github
https://learn.chatgpt.com/docs/code-review
https://learn.chatgpt.com/docs/auth
