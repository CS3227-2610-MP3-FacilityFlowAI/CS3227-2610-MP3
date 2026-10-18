# Technician batched-review configuration update

- Authority: user requested the simpler review process and runtime/token usage in the conversation, outside reports. Baseline12b9841, clean tree/index.
- Scope: Technician workflow/plan/task template only; application, shared role process, model assignments and committed T0-T2 implementations unchanged. T3 remains unapproved.
- Change: author-complete compile/database smoke, frozen candidate, one consolidated independent review alongside scoped tests, bundled owner fixes, focused fresh delta verification and one final verdict. Mechanical checks belong to coordinator automation. Actual defects and required checks remain gates; no fixed review-round limit grants a pass.
- Evidence: T2 review found two UI defects and four test/fixture issues; use the existing T2 artifacts as historical evidence rather than rewriting them.
- Metrics policy: future runtime/token summaries appear in conversation only; use task-local observed metadata, clear scope/cutoff, cached-input inclusion and unavailable markers. Transient collection stays outside committed reports/handoffs.
- Validation: scoped Markdown links/anchors, required workflow/permission/model guards, whitespace and exact Git scope checked before configuration commit. No application tests needed for documentation-only changes; no subagents invoked.
- Outcome: requested configuration prepared and checked; no claim of measured savings or new iteration execution. Final configuration commit identifies the files; resolve with git log -1 -- workflow/agents/technician-workflow.md.
