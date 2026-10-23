# 2026-10-23 Product website

- Date/timezone: 23 October 2026 (Asia/Singapore).
- AI/tool/agent role: Codex implementation and local verification; no subagents.
- Task: create the assignment's GitHub Pages product website, similar to MP2.
- Prompt/intention summary: introduce the MP3 product and provide useful paths
  to the live application and project documentation. User switched to
  `github-pages` and explicitly requested continuation on the current branch.
- Sources: repository AGENTS.md, README, User Guide, PRODUCT-SPEC.md,
  CONTEXT.md and reuse ledger; MP2 v1.0.1 (`9819b857`) homepage, Jekyll config
  and Pages workflow; GitHub's official custom Pages workflow documentation.
- Requirement IDs: **NFR-DOC-001** and the user's quoted product-website
  deliverable. The page describes existing role workflows and advisory AI
  boundaries; no new application behaviour is introduced.
- Files affected: `docs/site/`, `.github/workflows/pages.yml`,
  `docs/ProductWebsite.md`, `docs/UserGuide.md`, `README.md`,
  `workflow/REUSE.md` and this log.
- Decisions: static HTML/CSS with local assets and no JavaScript or build
  dependencies; upload only the website directory. Link to maintained guides
  on GitHub. Keep business workflow hosting on Vercel. Package PRs, deploy only
  from `master`. Record the MP2 publishing pattern as adapted and the homepage
  structure as concept-only reuse. Correct a stale TBD live URL in the User
  Guide, which already identifies the deployed URL in its introduction.
- AI suggestions accepted: implemented role-led product overview, explicit
  illustrative Request, human-control AI explanation, guide links and team.
  These implementation choices remain subject to human review.
- AI suggestions rejected: do not reuse MP2's desktop installation/download
  instructions or claim the new Pages URL is already live. Use GitHub Pages
  as requested instead of Sites hosting.
- Verification: Python standard-library HTML parser checked all 19 links,
  section targets, unique IDs, a single H1 and both local assets. SVG parsed
  successfully; homepage, stylesheet and favicon each returned HTTP 200 from
  the local preview. All linked MP3 documentation paths exist in the checkout.
  Browser inspection at a 500px viewport showed no horizontal overflow;
  lifecycle and resources navigation updated their hash targets. Narrow hero
  and lifecycle layouts were visually inspected. `git diff --check` passed.
- Verification limits: desktop viewport and hosted Pages deployment were not
  executed. GitHub CLI is unavailable on PATH; no repository settings, commits,
  pushes or merges were performed. The workflow requires GitHub execution to
  verify its deployment. No application code changed, so Java tests were not run.
- Security: website artifact contains only static public product material;
  no application, database or LLM credentials are required. Request example is
  fictional and labelled. Deployment permissions are scoped to the deployment
  job and PRs cannot deploy. Existing application CI/CD is unchanged.
- Remaining work: teammate review, merge to `master`, enable GitHub Actions as
  the Pages publishing source and verify the reported deployment URL. Steps
  are documented in `docs/ProductWebsite.md`.
- Human reviewer/outcome: pending; this is an AI-prepared session summary.
