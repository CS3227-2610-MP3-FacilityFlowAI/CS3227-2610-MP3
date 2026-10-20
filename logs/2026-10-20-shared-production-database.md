# 20 October 2026 existing database reused for Production

- User decision: explicitly reuse one existing database for Production and
  minimize changes. This supersedes earlier environment data/key separation for
  this coursework deployment only; NFR-DEP-001 and AI-SEC-019 amended via ADR-007.
- Agent: Codex; Ponytail, Supabase/Postgres skills and Context7 consulted.
- Scope: retain facilityflow-dev, Accounts/records, runtime role and certificate;
  reuse existing server-side Vercel settings. No schema/data/account mutation,
  migration, reseeding, initialization, new project or deployment performed.
- Remote configuration: queried Vercel metadata, confirmed ten global Preview
  variables and no conflicting Production values. PATCHed target only to add
  Production while preserving Preview targets, values and secret types; added
  SPRING_PROFILES_ACTIVE=production. No key/password value was printed or stored
  in repository artifacts. All eleven Production targets verified afterwards.
- Independent configuration checks: privately pulled non-secret Production
  configuration into an OS temporary directory, confirmed database URL/runtime
  username match existing local configuration (without printing them), restricted
  facilityflow_app identity, production profile, DEMO_DATA_ENABLED=false,
  SESSION_COOKIE_SECURE=true, packaged CA path and AI_ENABLED=true. Temporary
  file removed immediately. Secret values were not inspected by this check.
- Executed checks: existing deployment-gates regression passed; whitespace passed.
  No application/SQL code changed, so full Java/DB suites not repeated. Runtime
  Production candidate smoke has not run.
- Artifacts: ADR-007, three relevant specifications, deployment/setup guides,
  decision index, traceability/reuse, this evidence and handoff.
- Remaining external setup: GitHub production-candidate environment must contain
  existing synthetic Requester SMOKE_USERNAME/SMOKE_PASSWORD and project automation
  SMOKE_BYPASS_SECRET. Existing GitHub secret values cannot be retrieved from the
  public repository API; user can enter the same known values in that environment.
- Risks/accepted scope: Preview/local and Production write the same synthetic
  dataset and share sessions/AI limits. Known exposed credentials still require
  rotation. Human review before merge and Production promotion remain required.
- Human approval evidence: explicit user single-database instruction this session;
  teammate implementation review pending. No promotion enabled or performed.
- Fresh configuration/docs verification: pending.
Fresh reviewer reported no blockers after checking ADR/spec/runbook consistency,
unchanged data/runtime security and release gates; independently reran deployment
gate regression and whitespace checks. Remote setting verification was performed
by parent only, not independently repeated. GitHub secrets/candidate smoke and
human teammate review/promotion remain pending.
