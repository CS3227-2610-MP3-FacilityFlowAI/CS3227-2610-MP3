# Verified Session Logs

Store concise, human-verified summaries of AI-assisted development sessions in
this directory. Each summary should include the goal, relevant requirement IDs,
inputs, tools or agents used, changed files, tests and evidence, corrections,
unresolved risks, and the human review decision. Never include credentials,
tokens, raw secrets, or unnecessary personal data.

## Naming and required fields

Use `YYYY-MM-DD-short-description.md` (Asia/Singapore project date); add a distinct
suffix for multiple sessions. Use [the prompt-log template](../workflow/templates/prompt-log-template.md).

Capture date, AI/tool/agent role, task, sanitized prompt/intention summary,
source artifacts, files, decisions, requirement IDs, accepted/rejected suggestions,
commands and actual verification, corrections, risks, security and human review.

No fictional historical logs. AI-prepared summaries must state that human review
is pending until actually reviewed. Separate tool-verified facts from human
approval. This README keeps logs/ tracked; no .gitkeep is necessary.
